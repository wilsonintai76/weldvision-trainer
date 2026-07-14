package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Weld Apprentice",
    val level: Int = 3,
    val experiencePoints: Int = 1250,
    val gmawWeldTimeSeconds: Int = 240, // 4 mins initial GTAW/GMAW practice
    val gtawWeldTimeSeconds: Int = 95,
    val smawWeldTimeSeconds: Int = 180,
    val isSynced: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
