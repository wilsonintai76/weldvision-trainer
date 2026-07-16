package com.example.ui

import androidx.compose.ui.geometry.Offset
import com.example.data.SyncLogEntity

// ============================================================================
// 1. Enums
// ============================================================================

enum class AppScreen {
    LOGIN, REGISTER, CALIBRATE, SIMULATOR, SETTINGS, RESULTS, PROFILE, QR_SCANNER
}

enum class WeldProcess(val label: String, val abbrev: String) {
    GMAW("MIG / Gas Metal Arc", "GMAW")
}

enum class MaterialType(val label: String, val thermalCond: String, val meltPoint: String) {
    CARBON_STEEL("Carbon Steel (A36)", "Low", "1420°C")
}

enum class JointConfig(val label: String) {
    BUTT("Butt Joint (1G)")
}

enum class ClampingRestraint(val label: String) {
    NONE("None"),
    MEDIUM("Medium (Soft Clamps)")
}

enum class EnvironmentFactor(val label: String) {
    NORMAL("Normal (Indoor)"),
    WINDY("High Wind (Outdoor)"),
    HUMID("High Humidity")
}

enum class WeldMode(val label: String, val abbrev: String) {
    STRAIGHT("Straight / Stringer", "STR"),
    WEAVING("Weaving / Oscillating", "WEV")
}

// ============================================================================
// 2. Data Classes
// ============================================================================

data class WeldSession(
    val id: String,
    val timestamp: String,
    val process: String,
    val material: String,
    val joint: String,
    val grade: Int,
    val arcLengthStability: Int,
    val travelSpeedUniformity: Int,
    val angleOrientationStability: Int,
    val defectCount: Int,
    val porosityRisk: String,
    val coachingPhrase: String
)

data class WeldingModule(
    val id: String,
    val title: String,
    val process: WeldProcess,
    val joint: JointConfig,
    val material: MaterialType,
    val materialThickness: Float,
    val clampingRestraint: ClampingRestraint,
    val targetGap: Float,
    val targetSpeed: Float,
    val voltage: Float,
    val amperage: Int,
    val wireFeedSpeed: Int,
    val gasFlowRate: Float,
    val description: String,
    val levelRequired: Int = 1
)

val preDefinedModules = listOf(
    WeldingModule(
        id = "mig_butt", title = "MIG Butt Joint (1G)",
        process = WeldProcess.GMAW, joint = JointConfig.BUTT, material = MaterialType.CARBON_STEEL, materialThickness = 6.0f, clampingRestraint = ClampingRestraint.MEDIUM,
        targetGap = 3.0f, targetSpeed = 4.5f, voltage = 19.5f, amperage = 135, wireFeedSpeed = 230, gasFlowRate = 25.0f,
        description = "Beginner focus. Weld a flat-position butt joint with MIG. Keep arc gap around 3.0mm and travel at 4.5mm/s.",
        levelRequired = 1
    )
)

// ============================================================================
// 3. Central State
// ============================================================================

data class WeldVisionState(
    val currentScreen: AppScreen = AppScreen.LOGIN,
    val selectedModuleId: String = "mig_butt",
    val currentMaterial: MaterialType = MaterialType.CARBON_STEEL,
    val materialThickness: Float = 6.0f,
    val currentJoint: JointConfig = JointConfig.BUTT,
    val clampingRestraint: ClampingRestraint = ClampingRestraint.MEDIUM,
    val currentEnvironment: EnvironmentFactor = EnvironmentFactor.NORMAL,
    val isInstructorJoined: Boolean = false,
    val isInstructorAudioActive: Boolean = false,
    val instructorAnnotations: List<Offset> = emptyList(),
    val experiencePoints: Int = 1250,
    val userLevel: Int = 3,
    val defectCount: Int = 0,
    val porosityRisk: String = "Low",
    val travelSpeedScore: Float = 0.72f,
    val currentProcess: WeldProcess = WeldProcess.GMAW,
    val arcLength: Float = 3.0f,
    val workAngle: Int = 90,
    val travelAngle: Int = 10,
    val travelSpeed: Float = 4.0f,
    val targetSpeed: Float = 4.0f,
    val targetGap: Float = 3.0f,
    val voltage: Float = 18.5f,
    val amperage: Int = 140,
    val wireFeedSpeed: Int = 220,
    val gasFlowRate: Float = 25.0f,
    val currentUserId: Int? = null,
    val isCalibrating: Boolean = false,
    val calibrationProgress: Int = 0,
    val isCalibrated: Boolean = false,
    val calibrationStep: Int = 0, // 0: Idle/Done, 1: Focal Length, 2: Tip Offset
    val focalLengthStep: Int = 0, // 0: Close, 1: Medium, 2: Far
    val tipOffsetStep: Int = 0, // 0: Neutral, 1: Left, 2: Right, 3: Forward
    val calibrationOffsetX: Float = 0f,
    val calibrationOffsetY: Float = 0f,
    val calibrationOffsetZ: Float = 0f,
    val calibrationRefX: Float = 0f,  // WCS zero — tip world X at calibration
    val calibrationRefZ: Float = 0f,  // WCS zero — tip world Z at calibration
    val machineZeroWorkAngle: Float = 0f,    // MZ — first gyro work angle snap
    val machineZeroTravelAngle: Float = 0f,  // MZ — first gyro travel angle snap
    val hasMachineZero: Boolean = false,
    val isBleSynced: Boolean = true,
    val isVoiceFeedbackEnabled: Boolean = true,
    val isHapticFeedbackEnabled: Boolean = true,
    val isPowerSaveEnabled: Boolean = false,
    val batteryLevel: Float = 100f,
    val isBatterySimulated: Boolean = false,
    val isSimulationPaused: Boolean = false,
    val isProximitySimulated: Boolean = true,
    val isProximityFar: Boolean = false,
    val gyroWorkAngle: Float = 90f,
    val gyroTravelAngle: Float = 10f,
    val gyroAngularSpeed: Float = 0f,
    val isGyroActive: Boolean = false,
    val showOnboardingOverlay: Boolean = true,
    val isPracticeRunActive: Boolean = false,
    val weldProgress: Float = 0f,
    val scoreTicks: Int = 0,
    val scoreSum: Int = 0,
    val activeResultsTab: Int = 0,
    val lastGrade: Int = 88,
    val lastArcLengthStability: Int = 96,
    val lastTravelSpeedUniformity: Int = 72,
    val lastAngleOrientationStability: Int = 92,
    val lastCoachingPhrase: String = "Excellent control of your arc length which remained stable at a clean 3.1mm average. However, your travel speed was erratic and spiked near the end of the plate. Focus on maintaining a steady physical drag speed, ensuring your arm slides smoothly without wrist adjustments.",
    val sessionHistory: List<WeldSession> = listOf(
        WeldSession("session_1", "Jul 12, 2026 14:32", "GMAW", "Carbon Steel", "Butt Joint (1G)", 76, 82, 65, 80, 1, "Medium", "Moderate arc stability. Speed spiked near the weld finish, causing minor undercut defects.")
    ),
    val profileName: String = "Weld Apprentice",
    val matricNo: String = "",
    val gmawWeldTime: Int = 240,
    val isEditingName: Boolean = false,
    val editedName: String = "",
    val editedMatricNo: String = "",
    val unlockedAchievementTitle: String? = null,
    val unlockedAchievementDesc: String? = null,
    val syncLogs: List<SyncLogEntity> = emptyList(),
    val syncStatus: String = "Idle",
    val lastSyncTime: String = "Never",
    val serverUrl: String = "",
    val authToken: String = "",
    val isAutoSyncEnabled: Boolean = true,
    val deviceId: String = "",
    val trackedArcX: Float = -1f,
    val trackedArcY: Float = -1f,
    val isArcTracked: Boolean = false,
    val isMinimalHud: Boolean = false,
    val weldMode: WeldMode = WeldMode.STRAIGHT,
    // Angle stability tracking for scoring
    val travelAngleSum: Float = 0f,
    val travelAngleCount: Int = 0,
    val travelAngleVariance: Float = 0f,
    val workAngleSum: Float = 0f,
    val workAngleCount: Int = 0,
    val workAngleVariance: Float = 0f,
    // Hybrid tracker state (AprilTag position + gyro)
    val travelProgress: Float = 0f,
    val travelDistanceMm: Float = 0f,
    val tagVisible: Boolean = false,
    val tagTxMm: Float = 0f,
    val tagTyMm: Float = 0f,
    val tagTzMm: Float = 0f,
    val trackerTravelSpeed: Float = 0f,
    val isTraveling: Boolean = false,
    val isOnPath: Boolean = true,
    // Weave training metrics
    val weaveWidthDeg: Float = 0f,
    val weaveFrequencyHz: Float = 0f,
    val weaveSymmetry: Float = 1f,
    val weaveDwellRatio: Float = 0f,
    val weavePatternType: String = "STRINGER",
    val weaveQualityScore: Float = 0f,
    val activeWeaveTargetId: String = ""
)

// ============================================================================
// 4. Theme Colors
// ============================================================================

val DeepSpaceBlue = androidx.compose.ui.graphics.Color(0xFF0A0E1A)
val ContainerGrey = androidx.compose.ui.graphics.Color(0xFF181C26)
val BorderGrey = androidx.compose.ui.graphics.Color(0xFF2C3140)
val MutedText = androidx.compose.ui.graphics.Color(0xFF8A92A6)
val AccentCyan = androidx.compose.ui.graphics.Color(0xFF00E5FF)
val WarningAmber = androidx.compose.ui.graphics.Color(0xFFFFB74D)
val AlertRed = androidx.compose.ui.graphics.Color(0xFFFF5252)
val AlertEmerald = androidx.compose.ui.graphics.Color(0xFF4CAF50)
val OnPrimary = androidx.compose.ui.graphics.Color(0xFF0A0E1A)
