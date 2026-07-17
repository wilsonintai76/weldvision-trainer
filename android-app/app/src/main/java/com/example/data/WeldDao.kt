package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WeldDao {
    @Query("SELECT * FROM user_profile WHERE id = :userId LIMIT 1")
    fun getUserProfile(userId: Int): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = :userId LIMIT 1")
    suspend fun getUserProfileDirect(userId: Int): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserProfileEntity?

    @Query("SELECT * FROM user_profile ORDER BY lastUpdated DESC LIMIT 1")
    suspend fun getLastActiveUser(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM weld_sessions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllWeldSessions(userId: Int): Flow<List<WeldSessionEntity>>

    @Query("SELECT * FROM weld_sessions WHERE userId = :userId")
    suspend fun getAllWeldSessionsDirect(userId: Int): List<WeldSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeldSession(session: WeldSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeldSessions(sessions: List<WeldSessionEntity>)

    @Query("DELETE FROM weld_sessions WHERE id = :id")
    suspend fun deleteWeldSessionById(id: String)

    @Query("SELECT * FROM weld_sessions WHERE isSynced = 0 AND userId = :userId")
    suspend fun getUnsyncedWeldSessions(userId: Int): List<WeldSessionEntity>

    @Query("UPDATE weld_sessions SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markWeldSessionsAsSynced(ids: List<String>)

    @Query("UPDATE user_profile SET isSynced = 1 WHERE id = :userId")
    suspend fun markUserProfileAsSynced(userId: Int)

    @Query("SELECT * FROM sync_logs ORDER BY id DESC LIMIT :limit")
    fun getSyncLogs(limit: Int): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncLog(log: SyncLogEntity)

    @Query("DELETE FROM sync_logs")
    suspend fun clearSyncLogs()

    @Query("SELECT * FROM calibration WHERE id = 1 LIMIT 1")
    fun getCalibration(): Flow<CalibrationEntity?>

    @Query("SELECT * FROM calibration WHERE id = 1 LIMIT 1")
    suspend fun getCalibrationDirect(): CalibrationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCalibration(cal: CalibrationEntity)
}
