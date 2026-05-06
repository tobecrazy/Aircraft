package com.young.aircraft.data

import android.content.Context
import android.content.Intent

data class BannerDetails(
    val name: String,
    val description: String,
    val source: BannerDetailsSource
) {
    val downloadFileName: String
        get() = if (name.contains('.')) name else "$name.jpg"

    fun toImageModel(context: Context): Any =
        when (source) {
            is BannerDetailsSource.Local -> "android.resource://${context.packageName}/${source.resId}"
            is BannerDetailsSource.Network -> source.url
        }
}

sealed class BannerDetailsSource {
    data class Local(val resId: Int) : BannerDetailsSource()
    data class Network(val url: String) : BannerDetailsSource()
}

object BannerDetailsIntentContract {
    const val EXTRA_NAME = "extra_banner_name"
    const val EXTRA_DESCRIPTION = "extra_banner_description"
    const val EXTRA_SOURCE_TYPE = "extra_banner_source_type"
    const val EXTRA_RES_ID = "extra_banner_res_id"
    const val EXTRA_URL = "extra_banner_url"
    const val SOURCE_LOCAL = "local"
    const val SOURCE_NETWORK = "network"

    fun fromIntent(intent: Intent): BannerDetails? {
        val name = intent.getStringExtra(EXTRA_NAME) ?: return null
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        val source = when (intent.getStringExtra(EXTRA_SOURCE_TYPE)) {
            SOURCE_LOCAL -> {
                val resId = intent.getIntExtra(EXTRA_RES_ID, 0)
                if (resId == 0) return null
                BannerDetailsSource.Local(resId)
            }
            SOURCE_NETWORK -> BannerDetailsSource.Network(intent.getStringExtra(EXTRA_URL) ?: return null)
            else -> return null
        }
        return BannerDetails(name, description, source)
    }
}
