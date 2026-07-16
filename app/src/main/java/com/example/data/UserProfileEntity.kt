package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "Weld Apprentice",
    val email: String = "",
    val passwordHash: String = "",
    val matricNo: String = "",
    val level: Int = 3,
    val experiencePoints: Int = 1250,
    val gmawWeldTimeSeconds: Int = 240, // 4 mins initial GMAW practice
    val isSynced: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
