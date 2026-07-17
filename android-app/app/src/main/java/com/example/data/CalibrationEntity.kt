package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calibration")
data class CalibrationEntity(
    @PrimaryKey val id: Int = 1,
    val offsetX: Float = 0f,       // mm — camera-to-tip bracket X
    val offsetY: Float = 0f,       // mm — camera-to-tip bracket Y
    val offsetZ: Float = 0f,       // mm — camera-to-tip bracket Z
    val refX: Float = 0f,          // mm — WCS zero reference X at cal time
    val refZ: Float = 0f,          // mm — WCS zero reference Z at cal time
    val poseCount: Int = 0,
    val calibratedAt: Long = 0L,
    val isSynced: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
