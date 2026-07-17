package com.young.aircraft.utils

import android.util.Base64

/**
 * Parsing helpers for RFC 2397 `data:` URIs, specifically the
 * `data:image/<subtype>;base64,<payload>` form produced by the rich-text editor
 * and inbound rich content (e.g. `sectDesc` HTML fragments).
 *
 * Create by Young
 */
object DataUriUtils {

    private const val DATA_PREFIX = "data:"
    private const val BASE64_MARKER = ";base64,"

    /** True if [value] is a base64-encoded `data:` URI we can decode. */
    fun isBase64DataUri(value: String): Boolean =
        value.startsWith(DATA_PREFIX, ignoreCase = true) &&
            value.contains(BASE64_MARKER, ignoreCase = true)

    /**
     * Extracts the MIME type from a `data:` URI, e.g. `data:image/png;base64,...` -> `image/png`.
     * Returns null when [value] is not a `data:` URI.
     */
    fun mimeType(value: String): String? {
        if (!value.startsWith(DATA_PREFIX, ignoreCase = true)) return null
        val meta = value.substring(DATA_PREFIX.length).substringBefore(',')
        return meta.substringBefore(';').trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Derives a file extension from a `data:` image URI's MIME type
     * (e.g. `image/png` -> `png`, `image/svg+xml` -> `svg`). Falls back to `png`.
     */
    fun fileExtension(value: String): String {
        val subtype = mimeType(value)?.substringAfterLast('/').orEmpty()
        return when {
            subtype.isEmpty() -> "png"
            subtype.startsWith("svg") -> "svg"
            subtype == "jpeg" -> "jpg"
            else -> subtype.substringBefore('+').trim().ifEmpty { "png" }
        }
    }

    /**
     * Decodes the base64 payload of a `data:...;base64,...` URI.
     * Returns null when [value] is not a base64 data URI or the payload is malformed.
     */
    fun decodeBytes(value: String): ByteArray? {
        if (!isBase64DataUri(value)) return null
        val payload = value.substringAfter(BASE64_MARKER, "").trim()
        if (payload.isEmpty()) return null
        return runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull()
    }
}
