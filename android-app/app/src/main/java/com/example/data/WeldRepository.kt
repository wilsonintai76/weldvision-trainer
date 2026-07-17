package com.example.data

import kotlinx.coroutines.flow.Flow

class WeldRepository(private val weldDao: WeldDao) {
    fun getUserProfile(userId: Int): Flow<UserProfileEntity?> = weldDao.getUserProfile(userId)
    fun getAllWeldSessions(userId: Int): Flow<List<WeldSessionEntity>> = weldDao.getAllWeldSessions(userId)
    val syncLogs: Flow<List<SyncLogEntity>> = weldDao.getSyncLogs(30)

    suspend fun insertWeldSession(session: WeldSessionEntity) {
        weldDao.insertWeldSession(session)
    }

    suspend fun saveUserProfile(profile: UserProfileEntity) {
        weldDao.insertUserProfile(profile)
    }

    suspend fun getProfileDirect(userId: Int): UserProfileEntity? {
        return weldDao.getUserProfileDirect(userId)
    }

    suspend fun getUserByEmail(email: String): UserProfileEntity? {
        return weldDao.getUserByEmail(email)
    }

    suspend fun getLastActiveUser(): UserProfileEntity? {
        return weldDao.getLastActiveUser()
    }

    suspend fun getUnsyncedWeldSessions(userId: Int): List<WeldSessionEntity> {
        return weldDao.getUnsyncedWeldSessions(userId)
    }

    suspend fun markWeldSessionsAsSynced(ids: List<String>) {
        weldDao.markWeldSessionsAsSynced(ids)
    }

    suspend fun markUserProfileAsSynced(userId: Int) {
        weldDao.markUserProfileAsSynced(userId)
    }

    suspend fun getAllWeldSessionsDirect(userId: Int): List<WeldSessionEntity> {
        return weldDao.getAllWeldSessionsDirect(userId)
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
        val lastUser = weldDao.getLastActiveUser()
        if (lastUser == null) {
            // Populate default historic weld sessions for GMAW
            val initialSessions = listOf(
                WeldSessionEntity(
                    id = "session_initial_1",
                    userId = 1,
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
                    id = "session_initial_4",
                    userId = 1,
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
