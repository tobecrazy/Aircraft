package com.young.aircraft.providers

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.young.aircraft.data.GameDifficulty
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
}
