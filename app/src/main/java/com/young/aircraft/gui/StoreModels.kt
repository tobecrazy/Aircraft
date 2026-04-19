package com.young.aircraft.gui

import androidx.annotation.DrawableRes
import com.young.aircraft.R

enum class StoreItemType {
    AIRCRAFT, WEAPON, POWER_UP
}

data class StoreItem(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    @DrawableRes val imageResId: Int,
    val type: StoreItemType,
    val isOwned: Boolean = false
)

fun getMockStoreItems(): List<StoreItem> = listOf(
    StoreItem("jet_1", "Alpha Jet", "Standard issue combat jet.", 0, R.drawable.jet_plane_1, StoreItemType.AIRCRAFT, true),
    StoreItem("jet_2", "Beta Interceptor", "Fast and agile.", 500, R.drawable.jet_plane_2, StoreItemType.AIRCRAFT),
    StoreItem("jet_3", "Gamma Bomber", "Heavy armor and slow.", 1200, R.drawable.jet_plane_3, StoreItemType.AIRCRAFT),
    StoreItem("jet_4", "Delta Stealth", "Advanced stealth technology.", 2500, R.drawable.jet_plane_4, StoreItemType.AIRCRAFT),

    StoreItem("wpn_1", "Plasma Bullet", "Standard plasma projectiles.", 0, R.drawable.bullet_up, StoreItemType.WEAPON, true),
    StoreItem("wpn_2", "Homing Missile", "Tracks enemy targets.", 800, R.drawable.missile_1, StoreItemType.WEAPON),
    StoreItem("wpn_3", "Heavy Rocket", "Deals area damage.", 1500, R.drawable.rocket, StoreItemType.WEAPON),

    StoreItem("pwr_1", "Shield Generator", "Temporary invincibility.", 300, R.drawable.shield_1, StoreItemType.POWER_UP),
    StoreItem("pwr_2", "Medical Kit", "Restores aircraft HP.", 200, R.drawable.red_heart_1, StoreItemType.POWER_UP),
    StoreItem("pwr_3", "Time Freeze", "Slows down time.", 400, R.drawable.timer_1, StoreItemType.POWER_UP)
)
