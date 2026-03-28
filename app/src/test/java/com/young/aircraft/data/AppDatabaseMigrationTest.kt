package com.young.aircraft.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    private lateinit var context: Context
    private val dbName = "migration-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `migrations from 2027 to 2029 preserve existing player data`() = runBlocking {
        createVersion2027Database()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2027_2028, AppDatabase.MIGRATION_2028_2029)
            .allowMainThreadQueries()
            .build()

        val records = migratedDb.playerGameDataDao().getByPlayerId("player-1")

        assertEquals(1, records.size)
        assertEquals(3, records.first().level)
        assertEquals(5_000L, records.first().score)
        assertEquals(7, records.first().jetPlaneRes)
        assertEquals(GameDifficulty.NORMAL.persistedValue, records.first().difficulty)
        assertEquals(0, records.first().jetPlaneIndex)

        migratedDb.close()
    }

    private fun createVersion2027Database() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2027) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS `player_game_data` (
                                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    `player_id` TEXT NOT NULL,
                                    `level` INTEGER NOT NULL,
                                    `score` INTEGER NOT NULL,
                                    `jet_plane_res` INTEGER NOT NULL,
                                    `timestamp` INTEGER NOT NULL
                                )
                                """.trimIndent()
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    }
                )
                .build()
        )

        val db = helper.writableDatabase
        db.execSQL(
            """
            INSERT INTO `player_game_data` (`id`, `player_id`, `level`, `score`, `jet_plane_res`, `timestamp`)
            VALUES (1, 'player-1', 3, 5000, 7, 123456789)
            """.trimIndent()
        )
        db.version = 2027
        db.close()
        helper.close()
    }
}
