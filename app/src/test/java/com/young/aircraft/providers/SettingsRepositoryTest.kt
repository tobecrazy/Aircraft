package com.young.aircraft.providers

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `legacy default shared preferences are migrated into unified repository`() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(SettingsRepository.KEY_DIFFICULTY, GameDifficulty.HARD.persistedValue)
            .putBoolean(SettingsRepository.KEY_BACKGROUND_SOUND, false)
            .putBoolean(SettingsRepository.KEY_COMBAT_SOUND, false)
            .putBoolean(SettingsRepository.KEY_HIT_SHAKE_EFFECT, false)
            .putBoolean(SettingsRepository.KEY_INVINCIBLE_MODE, true)
            .commit()

        val repository = SettingsRepository(context)

        assertEquals(GameDifficulty.HARD, repository.getDifficulty())
        assertFalse(repository.isBackgroundSoundEnabled())
        assertFalse(repository.isCombatSoundEnabled())
        assertFalse(repository.isHitShakeEffectEnabled())
        assertTrue(repository.isInvincibleModeEnabled())
    }

    @Test
    fun `install id is generated once and then reused`() {
        val repository = SettingsRepository(context)

        val installId = repository.getOrCreateInstallId()
        val installIdAgain = repository.getOrCreateInstallId()

        assertNotNull(installId)
        assertEquals(installId, installIdAgain)
        assertTrue(installId.isNotBlank())
    }

    @Test
    fun `invincible mode can be toggled and persists`() {
        val repository = SettingsRepository(context)
        
        repository.setInvincibleModeEnabled(true)
        assertTrue(repository.isInvincibleModeEnabled())
        
        repository.setInvincibleModeEnabled(false)
        assertFalse(repository.isInvincibleModeEnabled())
    }

    @Test
    fun `hit shake effect can be toggled and persists`() {
        val repository = SettingsRepository(context)

        repository.setHitShakeEffectEnabled(false)
        assertFalse(repository.isHitShakeEffectEnabled())

        repository.setHitShakeEffectEnabled(true)
        assertTrue(repository.isHitShakeEffectEnabled())
    }

    @Test
    fun `puzzle guide completion flag can be toggled and persists`() {
        val repository = SettingsRepository(context)

        assertFalse(repository.isPuzzleGuideCompleted())

        repository.setPuzzleGuideCompleted(true)
        assertTrue(repository.isPuzzleGuideCompleted())

        repository.setPuzzleGuideCompleted(false)
        assertFalse(repository.isPuzzleGuideCompleted())
    }

    @Test
    fun `clearCachedGameData removes cache files without resetting settings`() {
        val repository = SettingsRepository(context)
        repository.setDifficulty(GameDifficulty.HARD)
        context.getSharedPreferences("puzzle_image_cache", Context.MODE_PRIVATE)
            .edit()
            .putString("cached_image_file", "puzzle_cached_image.jpg")
            .commit()
        val cacheFile = context.cacheDir.resolve("puzzle_cached_image.jpg")
        cacheFile.writeText("cached image")

        repository.clearCachedGameData()

        assertFalse(cacheFile.exists())
        assertFalse(
            context.getSharedPreferences("puzzle_image_cache", Context.MODE_PRIVATE)
                .contains("cached_image_file")
        )
        assertEquals(GameDifficulty.HARD, repository.getDifficulty())
    }

    @Test
    fun `getCachedGameDataSizeBytes returns recursive cache size`() {
        val repository = SettingsRepository(context)
        val nestedCacheDir = context.cacheDir.resolve("nested")
        nestedCacheDir.mkdirs()
        context.cacheDir.resolve("first.cache").writeBytes(ByteArray(12))
        nestedCacheDir.resolve("second.cache").writeBytes(ByteArray(8))

        assertEquals(20L, repository.getCachedGameDataSizeBytes())
    }
}
