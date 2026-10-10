package com.young.developtools.utils.apidebug

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.net.MalformedURLException
import java.net.URL

enum class UrlError {
    EMPTY,
    INVALID
}

enum class BodyError {
    /** Body text is not valid JSON. */
    INVALID_JSON,

    /** The HTTP method does not send a body, but one was filled in. */
    NOT_ALLOWED
}

/** HTTP methods that carry a request body in this tool. */
val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")

private val gson = Gson()

/** Returns null when the URL is usable, otherwise the reason it is not. */
fun validateUrl(url: String): UrlError? {
    if (url.isBlank()) return UrlError.EMPTY
    return try {
        val parsed = URL(url.trim())
        if (parsed.protocol != "http" && parsed.protocol != "https") {
            UrlError.INVALID
        } else {
            // toURI() rejects illegal characters (e.g. spaces) that URL tolerates.
            parsed.toURI()
            if (parsed.host.isNullOrEmpty()) UrlError.INVALID else null
        }
    } catch (e: MalformedURLException) {
        UrlError.INVALID
    } catch (e: Exception) {
        UrlError.INVALID
    }
}

/**
 * Returns null when the body is acceptable, otherwise the reason it is not.
 * A non-blank body must always be valid JSON; a body on a method that never
 * sends one (GET/DELETE/...) is rejected so it cannot be silently dropped.
 */
fun validateBody(method: String, body: String): BodyError? {
    if (body.isBlank()) return null
    if (method !in METHODS_WITH_BODY) return BodyError.NOT_ALLOWED
    return try {
        gson.fromJson(body, Any::class.java)
        null
    } catch (e: JsonSyntaxException) {
        BodyError.INVALID_JSON
    }
}
