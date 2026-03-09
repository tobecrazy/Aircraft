package com.young.aircraft.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PlayerGameData::class], version = 2026)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerGameDataDao(): PlayerGameDataDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aircraft_game.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
