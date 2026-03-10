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
            ).fallbackToDestructiveMigration(false).build()

            INSTANCE = instance
            instance
        }
    }
}