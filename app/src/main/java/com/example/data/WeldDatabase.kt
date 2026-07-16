package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UserProfileEntity::class, WeldSessionEntity::class, SyncLogEntity::class, CalibrationEntity::class], version = 5, exportSchema = false)
abstract class WeldDatabase : RoomDatabase() {
    abstract fun weldDao(): WeldDao

    companion object {
        @Volatile
        private var INSTANCE: WeldDatabase? = null

        fun getDatabase(context: Context): WeldDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WeldDatabase::class.java,
                    "weld_vision_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
