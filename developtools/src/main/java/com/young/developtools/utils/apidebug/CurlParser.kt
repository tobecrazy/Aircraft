package com.young.developtools.utils.apidebug

data class CurlRequest(
    val url: String,
    val method: String,
    val headers: List<Pair<String, String>>,
    val body: String?
)

class CurlParseException(message: String) : Exception(message)

/**
 * Parses a `curl` command line into method/url/headers/body.
 *
 * Supported: `-X/--request`, `-H/--header 'K: V'`, `-d/--data*` (multiple
 * `-d` are joined with `&`), `--url` or the first bare `http(s)://` token.
 * Other flags are ignored. Quoting follows shell conventions: single quotes
 * preserve everything literally, double quotes allow backslash escapes, and a
 * backslash-newline line continuation is treated as a space.
 */
fun parseCurl(command: String): CurlRequest {
    val normalized = command
        .replace("\\\r\n", " ")
        .replace("\\\n", " ")
    val tokens = tokenize(normalized).toMutableList()
    if (tokens.isEmpty()) throw CurlParseException("Empty command")
    if (tokens.first().trim().trim('"', '\'').substringAfterLast('/').lowercase() == "curl") {
        tokens.removeAt(0)
    }
    if (tokens.isEmpty()) throw CurlParseException("Empty command")

    var method: String? = null
    var methodExplicit = false
    var url: String? = null
    val headers = mutableListOf<Pair<String, String>>()
    val bodies = mutableListOf<String>()

    var i = 0
    while (i < tokens.size) {
        when (val token = tokens[i]) {
            "-X", "--request" -> {
                method = nextArg(tokens, ++i, token)
                methodExplicit = true
            }
            "-H", "--header" -> {
                val headerLine = nextArg(tokens, ++i, token)
                val colonIndex = headerLine.indexOf(':')
                if (colonIndex < 0) throw CurlParseException("Invalid header: $headerLine")
                val key = headerLine.substring(0, colonIndex).trim()
                if (key.isEmpty()) throw CurlParseException("Invalid header: $headerLine")
                headers.add(key to headerLine.substring(colonIndex + 1).trim())
            }
            "-d", "--data", "--data-raw", "--data-binary", "--data-ascii",
            "--data-urlencode" -> {
                bodies.add(nextArg(tokens, ++i, token))
            }
            "--url" -> {
                url = nextArg(tokens, ++i, token)
            }
            else -> {
                if (!token.startsWith("-") && url == null && isHttpUrl(token)) {
                    url = token
                }
                // Unknown flags and their values are ignored on purpose.
            }
        }
        i++
    }

    val finalUrl = url ?: throw CurlParseException("No URL found in curl command")
    val body = bodies.takeIf { it.isNotEmpty() }?.joinToString("&")
    val finalMethod = method ?: if (body != null) "POST" else "GET"
    return CurlRequest(
        url = finalUrl,
        method = finalMethod.uppercase(),
        headers = headers,
        body = body
    )
}

private fun nextArg(tokens: List<String>, index: Int, flag: String): String {
    if (index >= tokens.size) throw CurlParseException("Missing value for $flag")
    return tokens[index]
}

private fun isHttpUrl(token: String): Boolean {
    val lower = token.lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://")
}

/** Splits a shell-like command line, honouring single/double quotes and backslash escapes. */
internal fun tokenize(command: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var inSingle = false
    var inDouble = false
    var hasToken = false
    var i = 0
    while (i < command.length) {
        val c = command[i]
        when {
            c == '\\' && !inSingle && i + 1 < command.length -> {
                current.append(command[i + 1])
                hasToken = true
                i += 2
            }
            c == '\'' && !inDouble -> {
                inSingle = !inSingle
                hasToken = true
                i++
            }
            c == '"' && !inSingle -> {
                inDouble = !inDouble
                hasToken = true
                i++
            }
            c.isWhitespace() && !inSingle && !inDouble -> {
                if (hasToken) {
                    tokens.add(current.toString())
                    current.clear()
                    hasToken = false
                }
                i++
            }
            else -> {
                current.append(c)
                hasToken = true
                i++
            }
        }
    }
    if (hasToken) tokens.add(current.toString())
    return tokens
}
