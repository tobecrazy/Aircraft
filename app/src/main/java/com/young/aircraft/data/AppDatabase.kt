package com.young.aircraft.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PlayerGameData::class], version = 2030)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerGameDataDao(): PlayerGameDataDao

    companion object {
        val MIGRATION_2029_2030 = object : Migration(2029, 2030) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE player_game_data ADD COLUMN player_name TEXT")
            }
        }

        val MIGRATION_2028_2029 = object : Migration(2028, 2029) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE player_game_data ADD COLUMN jet_plane_index INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2027_2028 = object : Migration(2027, 2028) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE player_game_data ADD COLUMN difficulty TEXT NOT NULL DEFAULT '1.0'")
            }
        }
    }
}
