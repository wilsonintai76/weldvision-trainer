package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weld_sessions")
data class WeldSessionEntity(
    @PrimaryKey val id: String,
    val userId: Int = 1,
    val timestamp: String,
    val process: String, // "GMAW", "GTAW", "SMAW"
    val material: String,
    val joint: String,
    val grade: Int,
    val arcLengthStability: Int,
    val travelSpeedUniformity: Int,
    val angleOrientationStability: Int,
    val defectCount: Int,
    val porosityRisk: String,
    val coachingPhrase: String,
    val weldTimeSeconds: Int = 12,
    val isSynced: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
