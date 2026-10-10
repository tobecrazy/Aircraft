package com.young.developtools.utils.apidebug

/** Kind of problem found on one header line. */
enum class HeaderErrorKind {
    /** The line contains no ':'. */
    NO_COLON,

    /** The key part before ':' is blank. */
    EMPTY_KEY,

    /** The key contains characters illegal in an HTTP field name. */
    INVALID_KEY
}

data class HeaderError(
    /** 1-based line number inside the headers text box. */
    val lineNumber: Int,
    val kind: HeaderErrorKind
)

// RFC 7230 token characters allowed in an HTTP field name.
private val HEADER_KEY_PATTERN = Regex("^[!#\$%&'*+\\-.^_`|~0-9A-Za-z]+\$")

/**
 * Validates headers in "one `Key: Value` per line" format.
 *
 * Rules: blank lines are skipped; a line without ':' is [HeaderErrorKind.NO_COLON];
 * a blank key is [HeaderErrorKind.EMPTY_KEY]; a key with illegal characters is
 * [HeaderErrorKind.INVALID_KEY]. An empty value is allowed (HTTP permits it).
 */
fun validateHeaders(headersText: String): List<HeaderError> {
    val errors = mutableListOf<HeaderError>()
    headersText.lines().forEachIndexed { index, line ->
        if (line.isBlank()) return@forEachIndexed
        val lineNumber = index + 1
        val colonIndex = line.indexOf(':')
        if (colonIndex < 0) {
            errors.add(HeaderError(lineNumber, HeaderErrorKind.NO_COLON))
            return@forEachIndexed
        }
        val key = line.substring(0, colonIndex).trim()
        if (key.isEmpty()) {
            errors.add(HeaderError(lineNumber, HeaderErrorKind.EMPTY_KEY))
        } else if (!HEADER_KEY_PATTERN.matches(key)) {
            errors.add(HeaderError(lineNumber, HeaderErrorKind.INVALID_KEY))
        }
    }
    return errors
}

/** Parses the valid lines into a key-value map; invalid lines are skipped. */
fun parseHeaders(headersText: String): Map<String, String> {
    return headersText.lines()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2 && parts[0].trim().isNotEmpty()) {
                parts[0].trim() to parts[1].trim()
            } else {
                null
            }
        }
        .toMap()
}
