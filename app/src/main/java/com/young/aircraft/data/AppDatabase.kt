package com.young.aircraft.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PlayerGameData::class], version = 2028)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerGameDataDao(): PlayerGameDataDao

    companion object {
        val MIGRATION_2027_2028 = object : Migration(2027, 2028) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE player_game_data ADD COLUMN difficulty TEXT NOT NULL DEFAULT '1.0'")
            }
        }
    }
}
