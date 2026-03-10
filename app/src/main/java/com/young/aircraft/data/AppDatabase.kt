package com.young.aircraft.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PlayerGameData::class], version = 2026)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerGameDataDao(): PlayerGameDataDao
}
