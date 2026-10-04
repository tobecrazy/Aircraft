package com.young.aircraft.ui

import android.app.Activity
import android.content.Context
import com.young.aircraft.data.BossState
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

    @Test
    fun `boss fires a single missile above half hp and a symmetric spread of orbs below it`() {
        val bossEnemy = BossEnemy(context, speed = 0f)
        bossEnemy.spawnBoss(1)
        val boss = requireNotNull(bossEnemy.activeBoss)

        boss.hitPoints = boss.maxHitPoints
        invokeFireBomb(bossEnemy)
        assertEquals(1, boss.bombs.size)
        assertFalse(boss.bombs.single().isSpreadShot)
        assertEquals(0f, boss.bombs.single().vx, 0f)

        boss.bombs.clear()
        boss.hitPoints = boss.maxHitPoints * BossEnemy.SPREAD_HP_RATIO - 1f
        invokeFireBomb(bossEnemy)

        val spread = boss.bombs
        assertEquals(BossEnemy.SPREAD_SHOT_COUNT, spread.size)
        assertTrue(spread.all { it.isSpreadShot })
        // Symmetric spread: outermost shots mirror each other, and nobody fires straight up.
        assertEquals(-spread.first().vx, spread.last().vx, 0.0001f)
        assertEquals(0f, spread[spread.size / 2].vx, 0.0001f)
    }

    @Test
    fun `spread shot volleys have longer intervals at every level`() {
        val bossEnemy = BossEnemy(context, speed = 0f)

        bossEnemy.spawnBoss(1)
        assertEquals(80, bossEnemy.getBombFireInterval(isSpreadShot = false))
        assertEquals(120, bossEnemy.getBombFireInterval(isSpreadShot = true))

        bossEnemy.spawnBoss(10)
        assertEquals(21, bossEnemy.getBombFireInterval(isSpreadShot = false))
        assertEquals(31, bossEnemy.getBombFireInterval(isSpreadShot = true))
    }

    private fun invokeFireBomb(bossEnemy: BossEnemy) {
        val method = BossEnemy::class.java.getDeclaredMethod("fireBomb", BossState::class.java)
        method.isAccessible = true
        method.invoke(bossEnemy, bossEnemy.activeBoss)
    }
}
