package com.young.aircraft.ui

import android.app.Activity
import android.content.Context
import com.young.aircraft.data.EnemyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameCoreViewCollisionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    @Test
    fun `player bullet hit enemy removes collided bullet without breaking remaining bullets`() {
        val gameCoreView = GameCoreView(context).apply {
            drawAircraft = Aircraft(context, speed = 0f)
            enemies = Enemies(context, speed = 0f).also {
                it.activeEnemies.clear()
            }
            bossEnemy = BossEnemy(context, speed = 0f)
        }

        val bullets = gameCoreView.drawAircraft.getBullets() as MutableList<Bullet>
        val hitBullet = Bullet(x = 120f, y = 120f, originY = 120f)
        val trailingBullet = Bullet(x = 480f, y = 480f, originY = 480f)
        bullets.add(hitBullet)
        bullets.add(trailingBullet)

        val enemy = EnemyState(
            x = 120f,
            y = 120f,
            bitmap = null,
            health = 1f
        )
        gameCoreView.enemies.activeEnemies.add(enemy)

        invokeCheckPlayerBulletsHitEnemies(gameCoreView)

        val remainingBullets = gameCoreView.drawAircraft.getBullets()
        assertEquals(1, remainingBullets.size)
        assertSame(trailingBullet, remainingBullets.single())
        assertFalse(remainingBullets.contains(hitBullet))
        assertTrue(enemy.isDestroyed())
        assertEquals(1, gameCoreView.enemiesDestroyedThisLevel)
        assertEquals(1, gameCoreView.totalKills)
    }

    private fun invokeCheckPlayerBulletsHitEnemies(gameCoreView: GameCoreView) {
        val method = GameCoreView::class.java.getDeclaredMethod("checkPlayerBulletsHitEnemies")
        method.isAccessible = true
        method.invoke(gameCoreView)
    }
}
