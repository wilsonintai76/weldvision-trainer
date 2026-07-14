package com.example.data

import kotlinx.coroutines.flow.Flow

class WeldRepository(private val weldDao: WeldDao) {
    val userProfile: Flow<UserProfileEntity?> = weldDao.getUserProfile()
    val allWeldSessions: Flow<List<WeldSessionEntity>> = weldDao.getAllWeldSessions()
    val syncLogs: Flow<List<SyncLogEntity>> = weldDao.getSyncLogs(30)

    suspend fun insertWeldSession(session: WeldSessionEntity) {
        weldDao.insertWeldSession(session)
    }

    suspend fun saveUserProfile(profile: UserProfileEntity) {
        weldDao.insertUserProfile(profile)
    }

    suspend fun getProfileDirect(): UserProfileEntity? {
        return weldDao.getUserProfileDirect()
    }

    suspend fun getUnsyncedWeldSessions(): List<WeldSessionEntity> {
        return weldDao.getUnsyncedWeldSessions()
    }

    suspend fun markWeldSessionsAsSynced(ids: List<String>) {
        weldDao.markWeldSessionsAsSynced(ids)
    }

    suspend fun markUserProfileAsSynced() {
        weldDao.markUserProfileAsSynced()
    }

    suspend fun getAllWeldSessionsDirect(): List<WeldSessionEntity> {
        return weldDao.getAllWeldSessionsDirect()
    }

    suspend fun insertWeldSessions(sessions: List<WeldSessionEntity>) {
        weldDao.insertWeldSessions(sessions)
    }

    suspend fun insertSyncLog(log: SyncLogEntity) {
        weldDao.insertSyncLog(log)
    }

    suspend fun clearSyncLogs() {
        weldDao.clearSyncLogs()
    }

    val calibration: Flow<CalibrationEntity?> = weldDao.getCalibration()

    suspend fun getCalibrationDirect(): CalibrationEntity? = weldDao.getCalibrationDirect()
    suspend fun saveCalibration(cal: CalibrationEntity) = weldDao.saveCalibration(cal)

    suspend fun initializeDatabaseIfEmpty() {
        val existingProfile = weldDao.getUserProfileDirect()
        if (existingProfile == null) {
            // Populate default user profile
            weldDao.insertUserProfile(UserProfileEntity())

            // Populate some historic weld sessions for GMAW, GTAW, SMAW to show rich trends!
            val initialSessions = listOf(
                WeldSessionEntity(
                    id = "session_initial_1",
                    timestamp = "Jul 12, 2026 14:32",
                    process = "GMAW",
                    material = "Carbon Steel",
                    joint = "Butt Joint (1G)",
                    grade = 76,
                    arcLengthStability = 82,
                    travelSpeedUniformity = 65,
                    angleOrientationStability = 80,
                    defectCount = 1,
                    porosityRisk = "Medium",
                    coachingPhrase = "Moderate arc stability. However, speed fluctuated near middle of weld.",
                    weldTimeSeconds = 12
                ),
                WeldSessionEntity(
                    id = "session_initial_2",
                    timestamp = "Jul 11, 2026 09:15",
                    process = "GTAW",
                    material = "Aluminum",
                    joint = "Tee Joint (2F)",
                    grade = 81,
                    arcLengthStability = 85,
                    travelSpeedUniformity = 78,
                    angleOrientationStability = 82,
                    defectCount = 0,
                    porosityRisk = "Low",
                    coachingPhrase = "Nice and clean bead with stable travel speed. Minor angle variation noted.",
                    weldTimeSeconds = 12
                ),
                WeldSessionEntity(
                    id = "session_initial_3",
                    timestamp = "Jul 10, 2026 16:45",
                    process = "SMAW",
                    material = "Carbon Steel",
                    joint = "Lap Joint (2F)",
                    grade = 69,
                    arcLengthStability = 70,
                    travelSpeedUniformity = 62,
                    angleOrientationStability = 75,
                    defectCount = 2,
                    porosityRisk = "High",
                    coachingPhrase = "Weld was deposited with excessive arc length. Tighten your stick gap.",
                    weldTimeSeconds = 12
                ),
                WeldSessionEntity(
                    id = "session_initial_4",
                    timestamp = "Jul 13, 2026 11:20",
                    process = "GMAW",
                    material = "Titanium",
                    joint = "Lap Joint (2F)",
                    grade = 88,
                    arcLengthStability = 96,
                    travelSpeedUniformity = 72,
                    angleOrientationStability = 92,
                    defectCount = 0,
                    porosityRisk = "Low",
                    coachingPhrase = "Excellent control of your arc length which remained stable at a clean 3.1mm average. Travel speed was generally consistent.",
                    weldTimeSeconds = 12
                )
            )
            for (session in initialSessions) {
                weldDao.insertWeldSession(session)
            }
        }
    }
}
