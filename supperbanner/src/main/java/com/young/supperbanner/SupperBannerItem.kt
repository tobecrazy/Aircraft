package com.young.supperbanner

import androidx.annotation.DrawableRes

data class SupperBannerItem(
    val name: String,
    val description: String,
    val image: SupperBannerImage
)

sealed class SupperBannerImage {
    data class Local(@param:DrawableRes val resId: Int) : SupperBannerImage()
    data class Network(val url: String) : SupperBannerImage()
}
