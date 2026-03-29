package com.young.aircraft.providers

import android.content.Context
import androidx.room.Room
import com.young.aircraft.data.AppDatabase

/**
 * Create by Young
 **/
object DatabaseProvider {

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "aircraft_game.db"
            )
                .addMigrations(AppDatabase.MIGRATION_2027_2028, AppDatabase.MIGRATION_2028_2029)
                .build()

            INSTANCE = instance
            instance
        }
    }

    /**
     * For testing purposes
     */
    fun setDatabase(db: AppDatabase?) {
        INSTANCE = db
    }
}
