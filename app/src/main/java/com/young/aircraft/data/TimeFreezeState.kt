package com.young.aircraft.data

/**
 * Represents a time freeze power-up on the screen.
 * When collected by player, enemies freeze.
 * When collected by enemy, player freezes.
 */
data class TimeFreezeState(
    val x: Float,
    val y: Float,
    val spawnFrame: Int,
    val bitmapIndex: Int,
    var collected: Boolean = false,
    var collectedByPlayer: Boolean = false  // true = player collected (enemies freeze), false = enemy collected (player freezes)
)
