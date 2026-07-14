package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: String,
    val status: String, // "SUCCESS", "FAILED", "IN_PROGRESS"
    val action: String, // "PUSH", "PULL", "FULL_SYNC"
    val details: String,
    val bytesTransferred: Int = 0,
    val durationMs: Long = 0L
)
