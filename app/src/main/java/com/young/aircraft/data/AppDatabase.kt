package com.young.aircraft.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PlayerGameData::class], version = 2031)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerGameDataDao(): PlayerGameDataDao

    companion object {
        val MIGRATION_2030_2031 = object : Migration(2030, 2031) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE player_game_data ADD COLUMN air_battle_level INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE player_game_data ADD COLUMN puzzle_level INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE player_game_data ADD COLUMN game_mode TEXT NOT NULL DEFAULT 'AIR_BATTLE'")
                db.execSQL("ALTER TABLE player_game_data ADD COLUMN puzzle_score INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE player_game_data ADD COLUMN total_kills INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE player_game_data SET air_battle_level = level, puzzle_level = level")
            }
        }

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
