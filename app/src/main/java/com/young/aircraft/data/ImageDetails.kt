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

    // Strings longer than this are handed off via an in-memory store instead of the Intent
    // Binder transaction, to avoid TransactionTooLargeException (e.g. large base64 data URIs).
    private const val MAX_INLINE_EXTRA_LENGTH = 64 * 1024
    private const val PAYLOAD_REF_PREFIX = "payload-ref:"
    private const val PAYLOAD_CACHE_SIZE = 4

    private val payloadSeq = java.util.concurrent.atomic.AtomicLong(0)
    private val payloadCache = object : LinkedHashMap<String, String>(PAYLOAD_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > PAYLOAD_CACHE_SIZE
    }

    /**
     * Returns a value safe to place in an Intent extra: short strings pass through unchanged,
     * long strings are stored in-process and replaced by a lightweight reference token.
     */
    @Synchronized
    fun toIntentExtra(value: String): String {
        if (value.length < MAX_INLINE_EXTRA_LENGTH) return value
        val token = "$PAYLOAD_REF_PREFIX${payloadSeq.incrementAndGet()}"
        payloadCache[token] = value
        return token
    }

    @Synchronized
    private fun fromIntentExtra(value: String?): String? {
        if (value == null || !value.startsWith(PAYLOAD_REF_PREFIX)) return value
        return payloadCache[value]
    }

    fun fromIntent(intent: Intent): ImageDetails? {
        val name = intent.getStringExtra(EXTRA_NAME) ?: return null
        val description = fromIntentExtra(intent.getStringExtra(EXTRA_DESCRIPTION)).orEmpty()
        val source = when (intent.getStringExtra(EXTRA_SOURCE_TYPE)) {
            SOURCE_LOCAL -> {
                val resId = intent.getIntExtra(EXTRA_RES_ID, 0)
                if (resId == 0) return null
                ImageDetailsSource.Local(resId)
            }
            SOURCE_NETWORK -> {
                val url = fromIntentExtra(intent.getStringExtra(EXTRA_URL)) ?: return null
                ImageDetailsSource.Network(url)
            }
            else -> return null
        }
        return ImageDetails(name, description, source)
    }
}
