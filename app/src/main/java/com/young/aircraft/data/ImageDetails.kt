package com.young.aircraft.data

import android.content.Context
import android.content.Intent

data class ImageDetails(
    val name: String,
    val description: String,
    val source: ImageDetailsSource
) {
    val downloadFileName: String
        get() = if (name.contains('.')) name else "$name.jpg"

    fun toImageModel(context: Context): Any =
        when (source) {
            is ImageDetailsSource.Local -> "android.resource://${context.packageName}/${source.resId}"
            is ImageDetailsSource.Network -> source.url
        }
}

sealed class ImageDetailsSource {
    data class Local(val resId: Int) : ImageDetailsSource()
    data class Network(val url: String) : ImageDetailsSource()
}

object ImageDetailsIntentContract {
    const val EXTRA_NAME = "extra_image_name"
    const val EXTRA_DESCRIPTION = "extra_image_description"
    const val EXTRA_SOURCE_TYPE = "extra_image_source_type"
    const val EXTRA_RES_ID = "extra_image_res_id"
    const val EXTRA_URL = "extra_image_url"
    const val SOURCE_LOCAL = "local"
    const val SOURCE_NETWORK = "network"

    fun fromIntent(intent: Intent): ImageDetails? {
        val name = intent.getStringExtra(EXTRA_NAME) ?: return null
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        val source = when (intent.getStringExtra(EXTRA_SOURCE_TYPE)) {
            SOURCE_LOCAL -> {
                val resId = intent.getIntExtra(EXTRA_RES_ID, 0)
                if (resId == 0) return null
                ImageDetailsSource.Local(resId)
            }
            SOURCE_NETWORK -> ImageDetailsSource.Network(intent.getStringExtra(EXTRA_URL) ?: return null)
            else -> return null
        }
        return ImageDetails(name, description, source)
    }
}
