package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class UserProfileDto(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String,
    @Json(name = "matricNo") val matricNo: String,
    @Json(name = "level") val level: Int,
    @Json(name = "experiencePoints") val experiencePoints: Int,
    @Json(name = "gmawWeldTimeSeconds") val gmawWeldTimeSeconds: Int,
    @Json(name = "lastUpdated") val lastUpdated: Long
)

@JsonClass(generateAdapter = true)
data class WeldSessionDto(
    @Json(name = "id") val id: String,
    @Json(name = "userId") val userId: Int,
    @Json(name = "timestamp") val timestamp: String,
    @Json(name = "process") val process: String,
    @Json(name = "material") val material: String,
    @Json(name = "joint") val joint: String,
    @Json(name = "grade") val grade: Int,
    @Json(name = "arcLengthStability") val arcLengthStability: Int,
    @Json(name = "travelSpeedUniformity") val travelSpeedUniformity: Int,
    @Json(name = "angleOrientationStability") val angleOrientationStability: Int,
    @Json(name = "defectCount") val defectCount: Int,
    @Json(name = "porosityRisk") val porosityRisk: String,
    @Json(name = "coachingPhrase") val coachingPhrase: String,
    @Json(name = "weldTimeSeconds") val weldTimeSeconds: Int,
    @Json(name = "lastUpdated") val lastUpdated: Long
)

@JsonClass(generateAdapter = true)
data class SyncResponseDto(
    @Json(name = "status") val status: String,
    @Json(name = "message") val message: String,
    @Json(name = "syncedIds") val syncedIds: List<String>,
    @Json(name = "serverTime") val serverTime: Long
)

@JsonClass(generateAdapter = true)
data class BulkSyncRequestDto(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "profile") val profile: UserProfileDto,
    @Json(name = "sessions") val sessions: List<WeldSessionDto>,
    @Json(name = "lastSyncTime") val lastSyncTime: Long
)

@JsonClass(generateAdapter = true)
data class BulkSyncResponseDto(
    @Json(name = "status") val status: String,
    @Json(name = "profile") val profile: UserProfileDto,
    @Json(name = "sessions") val sessions: List<WeldSessionDto>,
    @Json(name = "serverTime") val serverTime: Long
)

interface WeldSyncApi {
    @GET("sync/full")
    suspend fun fetchAllRemoteData(
        @Header("Authorization") authHeader: String,
        @Query("deviceId") deviceId: String,
        @Query("lastSyncTime") lastSyncTime: Long
    ): BulkSyncResponseDto

    @POST("sync/full")
    suspend fun performBulkSync(
        @Header("Authorization") authHeader: String,
        @Body request: BulkSyncRequestDto
    ): BulkSyncResponseDto
}
