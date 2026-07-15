package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.tracking.BeadPhysics
import com.example.tracking.HybridWeldTracker
import com.example.tracking.WeaveAnalyzer
import com.example.tracking.WexelGrid
import com.example.tracking.WeldVisionJNI
import com.example.tracking.math.PivotCalibrator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class WeldVisionUiViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WeldRepository(WeldDatabase.getDatabase(application).weldDao())
    val syncManager = WeldSyncManager(application, repository)

    private val _state = MutableStateFlow(WeldVisionState())
    val state: StateFlow<WeldVisionState> = _state.asStateFlow()
    val weaveAnalyzer = WeaveAnalyzer()
    val hybridTracker = HybridWeldTracker()
    val beadGrid = WexelGrid()
    val beadPhysics = BeadPhysics(beadGrid)
    private val pivotCalibrator = PivotCalibrator()
    private var calibrationPoseCount = 0

    init {
        _state.update { state ->
            state.copy(
                serverUrl = syncManager.serverUrl,
                authToken = syncManager.authToken,
                isAutoSyncEnabled = syncManager.isAutoSyncEnabled,
                deviceId = syncManager.deviceId,
                lastSyncTime = if (syncManager.lastSyncTimestamp == 0L) "Never" else {
                    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(syncManager.lastSyncTimestamp))
                }
            )
        }
        viewModelScope.launch {
            repository.initializeDatabaseIfEmpty()
            
            // Auto-login last active user
            val lastUser = repository.getLastActiveUser()
            if (lastUser != null) {
                _state.update { it.copy(
                    currentUserId = lastUser.id,
                    currentScreen = AppScreen.SIMULATOR
                )}
            }
        }
        viewModelScope.launch {
            _state.map { it.currentUserId }.distinctUntilChanged().collectLatest { userId ->
                if (userId != null) {
                    launch {
                        repository.getUserProfile(userId).collect { profile ->
                            if (profile != null) _state.update { it.copy(
                                profileName = profile.name, matricNo = profile.matricNo, userLevel = profile.level, experiencePoints = profile.experiencePoints,
                                gmawWeldTime = profile.gmawWeldTimeSeconds, gtawWeldTime = profile.gtawWeldTimeSeconds,
                                smawWeldTime = profile.smawWeldTimeSeconds) }
                        }
                    }
                    launch {
                        repository.getAllWeldSessions(userId).collect { dbSessions ->
                            _state.update { state -> state.copy(sessionHistory = dbSessions.map {
                                WeldSession(it.id, it.timestamp, it.process, it.material, it.joint, it.grade,
                                    it.arcLengthStability, it.travelSpeedUniformity, it.angleOrientationStability,
                                    it.defectCount, it.porosityRisk, it.coachingPhrase)
                            }) }
                        }
                    }
                } else {
                     _state.update { it.copy(sessionHistory = emptyList()) }
                }
            }
        }
        viewModelScope.launch {
            syncManager.syncState.collect { syncState ->
                val (status, last) = when (syncState) {
                    is WeldSyncManager.SyncState.Idle -> "Idle" to _state.value.lastSyncTime
                    is WeldSyncManager.SyncState.Syncing -> "Syncing..." to _state.value.lastSyncTime
                    is WeldSyncManager.SyncState.Success -> "Success" to syncState.lastSync
                    is WeldSyncManager.SyncState.Error -> "Error: ${syncState.errorMessage}" to _state.value.lastSyncTime
                }
                _state.update { it.copy(syncStatus = status, lastSyncTime = last) }
            }
        }
        viewModelScope.launch {
            repository.syncLogs.collect { logs -> _state.update { it.copy(syncLogs = logs) } }
        }
        // Load persisted calibration
        viewModelScope.launch {
            repository.calibration.collect { cal ->
                if (cal != null && cal.poseCount >= pivotCalibrator.minSamples) {
                    _state.update { it.copy(
                        isCalibrated = true,
                        calibrationOffsetX = cal.offsetX,
                        calibrationOffsetY = cal.offsetY,
                        calibrationOffsetZ = cal.offsetZ,
                        calibrationRefX = cal.refX,
                        calibrationRefZ = cal.refZ
                    )}
                }
            }
        }
    }

    // ── Navigation ──
    fun navigateTo(screen: AppScreen) {
        _state.update { it.copy(currentScreen = screen,
            showOnboardingOverlay = if (screen == AppScreen.SIMULATOR) it.showOnboardingOverlay else false) }
    }
    fun setOnboardingOverlayVisible(visible: Boolean) { _state.update { it.copy(showOnboardingOverlay = visible) } }

    // ── Calibration ──
    fun runCalibration() {
        if (_state.value.isCalibrating) return
        pivotCalibrator.reset()
        calibrationPoseCount = 0
        _state.update { it.copy(isCalibrating = true, calibrationProgress = 0) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                for (i in 1..100) {
                    delay(60)
                    // Snapshot the latest full pose from native tracker
                    try {
                        val pose = FloatArray(16)
                        if (WeldVisionJNI.nativeGetLatestPose(pose) == 1) {
                            val (R, t) = WeldVisionJNI.decomposeHomogeneous(pose)
                            pivotCalibrator.addSample(R, t)
                            calibrationPoseCount++
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("WeldVisionVM", "Calibration pose sample failed", t)
                    }
                    _state.update { it.copy(calibrationProgress = i, isCalibrating = i < 100) }
                }
                // Solve for bracket offset
                val offset = pivotCalibrator.computeCalibrationOffset()
                if (offset != null && calibrationPoseCount >= pivotCalibrator.minSamples) {
                    // Compute WCS reference: tip world position right now
                    val cur = _state.value
                    val refTipX = cur.tagTxMm + offset[0]
                    val refTipZ = cur.tagTzMm + offset[2]
                    _state.update { it.copy(
                        isCalibrating = false, calibrationProgress = 100,
                        isCalibrated = true,
                        calibrationOffsetX = offset[0],
                        calibrationOffsetY = offset[1],
                        calibrationOffsetZ = offset[2],
                        calibrationRefX = refTipX,
                        calibrationRefZ = refTipZ
                    )}
                    // Persist to Room DB (including WCS reference)
                    repository.saveCalibration(CalibrationEntity(
                        offsetX = offset[0], offsetY = offset[1], offsetZ = offset[2],
                        refX = refTipX, refZ = refTipZ,
                        poseCount = calibrationPoseCount,
                        calibratedAt = System.currentTimeMillis()
                    ))
                } else {
                    // Not enough poses — mark as failed
                    _state.update { it.copy(isCalibrating = false, calibrationProgress = 0, isCalibrated = false) }
                }
            } catch (_: Throwable) {
                _state.update { it.copy(isCalibrating = false, calibrationProgress = 0, isCalibrated = false) }
            }
        }
    }

    fun resetCalibration() {
        pivotCalibrator.reset()
        calibrationPoseCount = 0
        _state.update { it.copy(isCalibrated = false, calibrationOffsetX = 0f, calibrationOffsetY = 0f, calibrationOffsetZ = 0f, calibrationProgress = 0) }
        viewModelScope.launch { repository.saveCalibration(CalibrationEntity()) }
    }

    // ── Gyroscope ──
    fun updateGyroData(workAngle: Float, travelAngle: Float, angularSpeed: Float) {
        val cur = _state.value
        // Snap machine zero on first valid gyro reading (like CNC power-on home)
        if (!cur.hasMachineZero) {
            _state.update { it.copy(
                machineZeroWorkAngle = workAngle,
                machineZeroTravelAngle = travelAngle,
                hasMachineZero = true
            )}
        }
        _state.update { it.copy(gyroWorkAngle = workAngle, gyroTravelAngle = travelAngle, gyroAngularSpeed = angularSpeed, isGyroActive = true) }
        // Feed weave analyzer
        val metrics = weaveAnalyzer.feedSample(travelAngle, workAngle, angularSpeed, System.currentTimeMillis())
        if (metrics != null) {
            _state.update { it.copy(
                weaveWidthDeg = metrics.weaveWidth,
                weaveFrequencyHz = metrics.weaveFrequency,
                weaveSymmetry = metrics.symmetry,
                weaveDwellRatio = metrics.dwellRatio,
                weavePatternType = metrics.patternType.name,
                weaveQualityScore = metrics.qualityScore
            )}
        }
    }

    fun updateAngleStats(travelAngle: Float, workAngle: Float) {
        _state.update {
            val newTravelCount = it.travelAngleCount + 1
            val newTravelSum = it.travelAngleSum + travelAngle
            val travelAvg = newTravelSum / newTravelCount
            val travelVar = kotlin.math.abs(travelAngle - travelAvg)
            val newWorkCount = it.workAngleCount + 1
            val newWorkSum = it.workAngleSum + workAngle
            val workAvg = newWorkSum / newWorkCount
            val workVar = kotlin.math.abs(workAngle - workAvg)
            it.copy(
                travelAngleSum = newTravelSum, travelAngleCount = newTravelCount,
                travelAngleVariance = travelVar,
                workAngleSum = newWorkSum, workAngleCount = newWorkCount,
                workAngleVariance = workVar
            )
        }
    }

    // ── Practice Run ──
    fun startPracticeRun() {
        weaveAnalyzer.reset()
        hybridTracker.reset()
        beadGrid.reset()
        _state.update { it.copy(isPracticeRunActive = true, isSimulationPaused = false, weldProgress = 0f, scoreTicks = 0, scoreSum = 0,
            travelAngleSum = 0f, travelAngleCount = 0, travelAngleVariance = 0f,
            workAngleSum = 0f, workAngleCount = 0, workAngleVariance = 0f,
            weaveWidthDeg = 0f, weaveFrequencyHz = 0f, weaveSymmetry = 1f, weaveDwellRatio = 0f,
            weavePatternType = "STRINGER", weaveQualityScore = 0f,
            travelProgress = 0f, travelDistanceMm = 0f) }
    }

    fun updateTagPose(tagTranslationMm: FloatArray?) {
        val cur = _state.value
        val ts = hybridTracker.update(
            tagTranslationMm, null // TODO: Wire up actual camera rotation Quaternion from JNI
        )
        _state.update { it.copy(
            travelProgress = ts.travelProgress,
            travelDistanceMm = ts.travelDistanceMm,
            tagVisible = ts.tagVisible,
            tagTxMm = ts.tagTranslationMm.getOrElse(0) { 0f },
            tagTyMm = ts.tagTranslationMm.getOrElse(1) { 0f },
            tagTzMm = ts.tagTranslationMm.getOrElse(2) { 0f },
            trackerTravelSpeed = ts.travelSpeedMmPerSec,
            isTraveling = ts.isTraveling,
            isOnPath = ts.isOnPath
        )}
        // Auto-advance practice progress from real position when tag is visible
        if (cur.isPracticeRunActive && !cur.isSimulationPaused && ts.tagVisible) {
            val posProgress = (ts.travelProgress * 100f).coerceIn(cur.weldProgress, 100f)
            _state.update { it.copy(weldProgress = posProgress) }
        }
    }
    fun cancelPracticeRun() { _state.update { it.copy(isPracticeRunActive = false, weldProgress = 0f) } }
    fun updatePracticeProgress(progress: Float) { _state.update { it.copy(weldProgress = progress) } }
    fun addPracticeScoreTick(score: Int) { _state.update { it.copy(scoreTicks = it.scoreTicks + 1, scoreSum = it.scoreSum + score) } }
    fun resetSession() { _state.update { it.copy(currentScreen = AppScreen.SIMULATOR, isPracticeRunActive = false, weldProgress = 0f, scoreTicks = 0, scoreSum = 0) } }

    // ── Settings ──
    fun togglePowerSaveMode() { _state.update { it.copy(isPowerSaveEnabled = !it.isPowerSaveEnabled) } }
    fun toggleMinimalHud() { _state.update { it.copy(isMinimalHud = !it.isMinimalHud) } }

    fun toggleWeldMode() {
        _state.update {
            val next = if (it.weldMode == WeldMode.STRAIGHT) WeldMode.WEAVING else WeldMode.STRAIGHT
            val targetId = if (next == WeldMode.WEAVING && it.activeWeaveTargetId.isEmpty()) "weave_beginner_01" else it.activeWeaveTargetId
            it.copy(weldMode = next, activeWeaveTargetId = targetId)
        }
    }

    fun selectWeaveTarget(targetId: String) {
        _state.update { it.copy(activeWeaveTargetId = targetId,
            weldMode = if (targetId.isNotEmpty()) WeldMode.WEAVING else it.weldMode) }
    }
    fun toggleVoiceFeedback() { _state.update { it.copy(isVoiceFeedbackEnabled = !it.isVoiceFeedbackEnabled) } }
    fun toggleHapticFeedback() { _state.update { it.copy(isHapticFeedbackEnabled = !it.isHapticFeedbackEnabled) } }
    fun setSimulationPaused(paused: Boolean) { _state.update { it.copy(isSimulationPaused = paused) } }
    fun setProximitySimulated(enabled: Boolean) { _state.update { it.copy(isProximitySimulated = enabled, isSimulationPaused = if (enabled) it.isProximityFar else it.isSimulationPaused) } }
    fun setProximityFar(far: Boolean) { _state.update { it.copy(isProximityFar = far, isSimulationPaused = if (it.isProximitySimulated) far else it.isSimulationPaused) } }
    fun updateBatteryLevel(level: Float) { _state.update { if (it.isBatterySimulated) it else it.copy(batteryLevel = level, isPowerSaveEnabled = if (level < 20f) true else it.isPowerSaveEnabled) } }
    fun setSimulatedBattery(enabled: Boolean) { _state.update { it.copy(isBatterySimulated = enabled) } }
    fun updateSimulatedBatteryLevel(level: Float) { _state.update { it.copy(batteryLevel = level, isPowerSaveEnabled = if (level < 20f) true else it.isPowerSaveEnabled) } }

    // ── Training Module ──
    fun selectTrainingModule(moduleId: String) {
        val m = preDefinedModules.firstOrNull { it.id == moduleId } ?: return
        _state.update { it.copy(selectedModuleId = moduleId, currentProcess = m.process, currentJoint = m.joint,
            currentMaterial = m.material, targetGap = m.targetGap, targetSpeed = m.targetSpeed,
            voltage = m.voltage, amperage = m.amperage, wireFeedSpeed = m.wireFeedSpeed, gasFlowRate = m.gasFlowRate,
            isPracticeRunActive = false, weldProgress = 0f, scoreTicks = 0, scoreSum = 0) }
    }
    fun updateProcess(process: WeldProcess) { _state.update { it.copy(currentProcess = process) } }
    fun updateMaterial(material: MaterialType) { _state.update { it.copy(currentMaterial = material) } }
    fun updateJoint(joint: JointConfig) { _state.update { it.copy(currentJoint = joint) } }
    fun updateEnvironment(env: EnvironmentFactor) { _state.update { it.copy(currentEnvironment = env) } }
    fun toggleInstructor() { _state.update { it.copy(isInstructorJoined = !it.isInstructorJoined, isInstructorAudioActive = !it.isInstructorJoined) } }
    fun addInstructorAnnotation(offset: androidx.compose.ui.geometry.Offset) { _state.update { it.copy(instructorAnnotations = it.instructorAnnotations + offset) } }
    fun updateArcLength(length: Float) { _state.update { it.copy(arcLength = length) } }
    fun updateWorkAngle(angle: Int) { _state.update { it.copy(workAngle = angle) } }
    fun updateTravelAngle(angle: Int) { _state.update { it.copy(travelAngle = angle) } }
    fun updateTargetSpeed(speed: Float) { _state.update { it.copy(targetSpeed = speed) } }
    fun updateTargetGap(gap: Float) { _state.update { it.copy(targetGap = gap) } }
    fun adjustVoltage(delta: Float) { _state.update { it.copy(voltage = (it.voltage + delta).coerceIn(10f, 35f)) } }
    fun adjustFeedSpeed(delta: Int) { _state.update { it.copy(wireFeedSpeed = (it.wireFeedSpeed + delta).coerceIn(50, 600)) } }
    fun adjustAmperage(delta: Int) { _state.update { it.copy(amperage = (it.amperage + delta).coerceIn(30, 300)) } }
    fun adjustGasFlowRate(delta: Float) { _state.update { it.copy(gasFlowRate = (it.gasFlowRate + delta).coerceIn(0f, 50f)) } }
    fun updateTravelSpeed(speed: Float) { _state.update { it.copy(travelSpeed = speed) } }
    fun updateTrackedArc(x: Float, y: Float, isTracked: Boolean) { _state.update { it.copy(trackedArcX = x, trackedArcY = y, isArcTracked = isTracked) } }

    // ── Results ──
    fun setResultsTab(tab: Int) { _state.update { it.copy(activeResultsTab = tab) } }

    // ── Profile ──
    fun startEditingName() { _state.update { it.copy(isEditingName = true, editedName = it.profileName, editedMatricNo = it.matricNo) } }
    fun updateEditedName(name: String) { _state.update { it.copy(editedName = name) } }
    fun updateEditedMatricNo(matric: String) { _state.update { it.copy(editedMatricNo = matric) } }
    fun saveProfileData() {
        viewModelScope.launch {
            val n = _state.value.editedName.trim()
            val m = _state.value.editedMatricNo.trim()
            if (n.isNotEmpty()) { val p = repository.getProfileDirect(_state.value.currentUserId ?: 1) ?: UserProfileEntity(); repository.saveUserProfile(p.copy(name = n, matricNo = m)) }
            _state.update { it.copy(isEditingName = false) }
        }
    }
    fun cancelEditingName() { _state.update { it.copy(isEditingName = false) } }
    fun dismissAchievementToast() { _state.update { it.copy(unlockedAchievementTitle = null, unlockedAchievementDesc = null) } }

    // ── Sync ──
    fun performCloudSync() { viewModelScope.launch { _state.value.currentUserId?.let { syncManager.sync(it.toLong()) } } }
    fun clearSyncDatabaseLogs() { viewModelScope.launch { repository.clearSyncLogs() } }
    fun updateSyncSettings(serverUrl: String, authToken: String, isAutoSyncEnabled: Boolean) {
        syncManager.serverUrl = serverUrl; syncManager.authToken = authToken; syncManager.isAutoSyncEnabled = isAutoSyncEnabled
        _state.update { it.copy(serverUrl = serverUrl, authToken = authToken, isAutoSyncEnabled = isAutoSyncEnabled) }
    }

    // ── Complete Practice Run ──
    fun completePracticeRun(avgGrade: Int, arcLengthStability: Int, travelSpeedUniformity: Int,
                            angleOrientationStability: Int, defectCount: Int, porosityRisk: String, coachingPhrase: String) {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.US)
        val ts = sdf.format(java.util.Date())
        _state.update { it.copy(isPracticeRunActive = false, weldProgress = 100f, lastGrade = avgGrade,
            lastArcLengthStability = arcLengthStability, lastTravelSpeedUniformity = travelSpeedUniformity,
            lastAngleOrientationStability = angleOrientationStability, defectCount = defectCount,
            porosityRisk = porosityRisk, lastCoachingPhrase = coachingPhrase, activeResultsTab = 0, currentScreen = AppScreen.RESULTS) }
        viewModelScope.launch {
            val cur = _state.value; val prev = cur.sessionHistory
            val sessionEntity = WeldSessionEntity(id = "session_${System.currentTimeMillis()}", userId = cur.currentUserId ?: 1, timestamp = ts,
                process = cur.currentProcess.abbrev, material = cur.currentMaterial.label, joint = cur.currentJoint.label,
                grade = avgGrade, arcLengthStability = arcLengthStability, travelSpeedUniformity = travelSpeedUniformity,
                angleOrientationStability = angleOrientationStability, defectCount = defectCount,
                porosityRisk = porosityRisk, coachingPhrase = coachingPhrase, weldTimeSeconds = 12)
            repository.insertWeldSession(sessionEntity)
            val profile = repository.getProfileDirect(cur.currentUserId ?: 1) ?: UserProfileEntity()
            var gmaw = profile.gmawWeldTimeSeconds; var gtaw = profile.gtawWeldTimeSeconds; var smaw = profile.smawWeldTimeSeconds
            when (cur.currentProcess) { WeldProcess.GMAW -> gmaw += 12; WeldProcess.GTAW -> gtaw += 12; WeldProcess.SMAW -> smaw += 12 }
            repository.saveUserProfile(profile.copy(level = if (profile.experiencePoints + 150 >= 2000) 4 else profile.level,
                experiencePoints = profile.experiencePoints + 150, gmawWeldTimeSeconds = gmaw, gtawWeldTimeSeconds = gtaw, smawWeldTimeSeconds = smaw))
            if (syncManager.isAutoSyncEnabled) performCloudSync()
            // Achievements
            val prevPerfect = prev.any { it.grade >= 90 }; val prevNoDef = prev.any { it.defectCount == 0 }
            val prevArc = prev.any { it.arcLengthStability >= 90 }; val prevSpeed = prev.any { it.travelSpeedUniformity >= 85 }
            var title: String? = null; var desc: String? = null
            when {
                !prevPerfect && avgGrade >= 90 -> { title = "PERFECT WELD ACCURACY"; desc = "Achieved a pristine score of $avgGrade%!" }
                !prevNoDef && defectCount == 0 -> { title = "PRISTINE BEAD"; desc = "Deposited a flawless weld with zero defects!" }
                !prevArc && arcLengthStability >= 90 -> { title = "ARC LENGTH MASTER"; desc = "Maintained excellent gap height control of $arcLengthStability%!" }
                !prevSpeed && travelSpeedUniformity >= 85 -> { title = "STEADY-HAND OPERATOR"; desc = "Achieved ultra-uniform speed of $travelSpeedUniformity%!" }
            }
            if (title != null) _state.update { it.copy(unlockedAchievementTitle = title, unlockedAchievementDesc = desc) }
        }
    }

    // ── Authentication ──
    fun registerUser(email: String, name: String, matricNo: String, passwordHash: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val existing = repository.getUserByEmail(email)
                if (existing != null) {
                    onError("Email already registered")
                    return@launch
                }
                val newUser = UserProfileEntity(
                    id = 0, // Auto-generated
                    name = name,
                    email = email,
                    matricNo = matricNo,
                    passwordHash = passwordHash
                )
                repository.saveUserProfile(newUser)
                val insertedUser = repository.getUserByEmail(email)
                if (insertedUser != null) {
                    _state.update { it.copy(currentUserId = insertedUser.id, currentScreen = AppScreen.SIMULATOR) }
                    onSuccess()
                } else {
                    onError("Failed to create user")
                }
            } catch (e: Exception) {
                onError("Error: ${e.message}")
            }
        }
    }

    fun loginUser(email: String, passwordHash: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val user = repository.getUserByEmail(email)
                if (user == null) {
                    onError("User not found")
                } else if (user.passwordHash != passwordHash) {
                    onError("Incorrect password")
                } else {
                    _state.update { it.copy(currentUserId = user.id, currentScreen = AppScreen.SIMULATOR) }
                    onSuccess()
                }
            } catch (e: Exception) {
                onError("Error: ${e.message}")
            }
        }
    }

    fun loginBiometric(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val user = repository.getLastActiveUser()
                if (user == null) {
                    onError("No previous user found. Please login with email/password first.")
                } else {
                    _state.update { it.copy(currentUserId = user.id, currentScreen = AppScreen.SIMULATOR) }
                    onSuccess()
                }
            } catch (e: Exception) {
                onError("Error: ${e.message}")
            }
        }
    }

    fun logout() {
        _state.update { it.copy(currentUserId = null, currentScreen = AppScreen.LOGIN) }
    }
}
