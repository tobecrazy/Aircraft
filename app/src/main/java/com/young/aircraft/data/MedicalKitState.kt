package com.young.aircraft.data

data class MedicalKitState(
    val x: Float,
    val y: Float,
    val spawnFrame: Int,
    val bitmapIndex: Int,
    var collected: Boolean = false
)
