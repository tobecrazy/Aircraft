package com.young.developtools.data

import java.util.Locale

/**
 * Card models and the ID card (mainland China) layout parser over ML Kit OCR
 * lines. Everything here is pure Kotlin so the layout heuristics stay
 * unit-testable without an Activity or the ML Kit model.
 *
 * On-device only: callers must not persist or upload the result.
 */
enum class CardType { BANK_CARD, ID_FRONT, ID_BACK }

/** Advisory frame-quality verdict shown while scanning; never blocks recognition. */
enum class ScanHint { BLUR, DARK, BRIGHT, GLARE }

/**
 * One OCR line plus its bounding box in bitmap pixels. The ID card parser needs
 * the position (not only the text) to keep portrait-side noise out of the
 * address and to follow multi-line fields.
 */
data class OcrLine(
    val text: String,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val centerX: Int get() = (left + right) / 2
}

data class IdCardFront(
    val name: String = "",
    val gender: String = "",
    val ethnicity: String = "",
    val birth: String = "",
    val address: String = "",
    val idNumber: String = "",
)

data class IdCardBack(
    val issuer: String = "",
    val validFrom: String = "",
    val validTo: String = "",
) {
    /** Human-readable validity, e.g. `2013.05.01 - 2033.05.01` or `2013.05.01 - 长期`. */
    val validity: String
        get() = when {
            validFrom.isBlank() && validTo.isBlank() -> ""
            validTo.isBlank() -> validFrom
            else -> "$validFrom - $validTo"
        }
}

private val idWeights = intArrayOf(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
private const val ID_CHECK_CODES = "10X98765432"

/**
 * GB 11643 checksum. Also rejects a number whose embedded birth date is
 * impossible, which catches most single-digit OCR slips even when the checksum
 * happens to line up.
 */
fun isIdNumberValid(idNumber: String): Boolean {
    if (idNumber.length != 18) return false
    val normalized = idNumber.uppercase(Locale.US)
    if (!normalized.take(17).all(Char::isDigit)) return false
    if (!normalized[17].isDigit() && normalized[17] != 'X') return false
    var sum = 0
    for (index in 0 until 17) {
        sum += (normalized[index] - '0') * idWeights[index]
    }
    if (ID_CHECK_CODES[sum % 11] != normalized[17]) return false
    return birthDateFromIdNumber(normalized) != null
}

/** `yyyy-MM-dd` encoded in digits 7-14, or null when the number is not 18 chars. */
fun birthDateFromIdNumber(idNumber: String): String? {
    if (idNumber.length != 18) return null
    val year = idNumber.substring(6, 10).toIntOrNull() ?: return null
    val month = idNumber.substring(10, 12).toIntOrNull() ?: return null
    val day = idNumber.substring(12, 14).toIntOrNull() ?: return null
    if (year !in 1900..2100 || month !in 1..12 || day !in 1..31) return null
    return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
}

/** 17th digit: odd is male, even is female. */
fun genderFromIdNumber(idNumber: String): String {
    val digit = idNumber.getOrNull(16)?.digitToIntOrNull() ?: return ""
    return if (digit % 2 == 1) "男" else "女"
}

private val idLabels = listOf("姓名", "性别", "民族", "出生", "住址", "公民身份号码", "签发机关", "有效期限")

private val idDigitConfusions = mapOf(
    'O' to '0', 'o' to '0', 'D' to '0', 'd' to '0', 'Q' to '0', 'q' to '0',
    'I' to '1', 'l' to '1', '|' to '1',
    'Z' to '2', 'z' to '2', 'S' to '5', 's' to '5',
    'B' to '8', 'b' to '8', 'G' to '6',
)

/** 17 digit-ish glyphs plus a trailing digit, X, or a confused glyph that maps to one. */
private val idCandidate = Regex("""[0-9XxOoDdQqIl|ZzSsBbGg]{18}""")

private fun String.stripSpaces(): String = replace(" ", "").replace("　", "")

private fun correctIdDigits(raw: String): String = buildString {
    raw.forEachIndexed { index, char ->
        append(
            when {
                index == 17 && (char == 'X' || char == 'x') -> 'X'
                char.isDigit() -> char
                else -> idDigitConfusions[char] ?: char
            }
        )
    }
}

/**
 * Finds a checksum-valid number. The label and the digits normally share a
 * line, but OCR sometimes splits them, so the joined text is searched as a
 * fallback. Only a valid checksum is accepted, which keeps address digits
 * ("1号") from being mistaken for the number.
 */
private fun extractIdNumber(ordered: List<OcrLine>): String? {
    ordered.forEach { line ->
        val text = line.text.stripSpaces()
        val tail = if (text.contains("公民身份号码")) text.substringAfter("公民身份号码") else text
        idCandidate.findAll(tail).forEach { match ->
            val corrected = correctIdDigits(match.value)
            if (isIdNumberValid(corrected)) return corrected
        }
    }
    val joined = ordered.joinToString("") { it.text.stripSpaces() }
    idCandidate.findAll(joined).forEach { match ->
        val corrected = correctIdDigits(match.value)
        if (isIdNumberValid(corrected)) return corrected
    }
    return null
}

private fun cleanName(raw: String): String {
    var cleaned = raw.filter { it in '\u4e00'..'\u9fa5' || it == '·' }
    // A mis-grouped row can append the next label ("张三性别男"); cut there
    // instead of discarding the whole name.
    idLabels.forEach { label -> cleaned = cleaned.substringBefore(label) }
    return cleaned.take(16)
}

private fun extractName(ordered: List<OcrLine>): String {
    val index = ordered.indexOfFirst { it.text.stripSpaces().contains("姓名") }
    if (index < 0) return ""
    val line = ordered[index]
    val inline = line.text.stripSpaces().substringAfter("姓名").trimStart(':', '：')
    if (inline.isNotBlank()) return cleanName(inline)
    // Value wrapped onto the next line: accept it only when it sits directly
    // below, otherwise a label line ("性别…") would be mistaken for the name.
    val next = ordered.getOrNull(index + 1) ?: return ""
    val lineHeight = (line.bottom - line.top).coerceAtLeast(1)
    if (next.top - line.top > lineHeight * 3) return ""
    return cleanName(next.text.stripSpaces())
}

private fun extractGender(texts: List<String>): String {
    val joined = texts.joinToString("")
    return Regex("""性别[：:]?\s*(男|女)""").find(joined)?.groupValues?.get(1).orEmpty()
}

private fun extractEthnicity(texts: List<String>): String {
    val joined = texts.joinToString("")
    val label = Regex("""民族[：:]?""").find(joined) ?: return ""
    val rest = joined.substring(label.range.last + 1)
    // Ethnicity names are 1-3 glyphs; the next label usually follows with no
    // separator once spaces are stripped ("民族汉出生…"), so cut at a label.
    val value = rest.takeWhile { it in '\u4e00'..'\u9fa5' }.take(4)
    return idLabels.fold(value) { acc, labelText -> acc.substringBefore(labelText) }
}

/**
 * Address lines run from "住址" down to "公民身份号码". Lines whose center sits
 * in the right-hand portrait column are skipped, and lines that start with
 * another label end the block so a mis-grouped row cannot leak in.
 */
private fun extractAddress(ordered: List<OcrLine>): String {
    val maxRight = ordered.maxOfOrNull { it.right } ?: 0
    val start = ordered.indexOfFirst { it.text.stripSpaces().contains("住址") }
    if (start < 0) return ""
    val builder = StringBuilder()
    for (index in start until ordered.size) {
        val line = ordered[index]
        val text = line.text.stripSpaces()
        if (index > start && text.contains("公民身份号码")) break
        if (index > start && idLabels.any { text.startsWith(it) }) break
        if (maxRight > 0 && line.centerX > maxRight * PORTRAIT_COLUMN_START) continue
        val value = if (index == start) {
            text.substringAfter("住址").trimStart(':', '：')
        } else {
            text
        }
        if (value.isBlank()) continue
        builder.append(value)
        if (builder.length > MAX_ADDRESS_LENGTH) break
    }
    return builder.toString()
}

/** Fraction of the card width where the portrait column starts. */
private const val PORTRAIT_COLUMN_START = 0.72f
private const val MAX_ADDRESS_LENGTH = 80

/**
 * Parses the portrait side. Returns null unless a checksum-valid number was
 * read: without it the frame is untrustworthy and must not enter the vote.
 */
fun parseIdCardFront(lines: List<OcrLine>): IdCardFront? {
    val ordered = lines
        .filter { it.text.isNotBlank() }
        .sortedWith(compareBy({ it.top }, { it.left }))
    if (ordered.isEmpty()) return null
    val idNumber = extractIdNumber(ordered) ?: return null
    val texts = ordered.map { it.text.stripSpaces() }
    return IdCardFront(
        name = extractName(ordered),
        // A checksum-valid number makes its embedded gender digit more reliable
        // than the printed label, which is read from a small line of text.
        gender = genderFromIdNumber(idNumber).ifBlank { extractGender(texts) },
        ethnicity = extractEthnicity(texts),
        birth = birthDateFromIdNumber(idNumber).orEmpty(),
        address = extractAddress(ordered),
        idNumber = idNumber,
    )
}

private val datePattern = Regex("""(\d{4})[.\-·]?(\d{1,2})[.\-·]?(\d{1,2})""")

private fun extractIssuer(texts: List<String>): String {
    texts.forEach { text ->
        val index = text.indexOf("签发机关")
        if (index >= 0) {
            val inline = text.substring(index + 4).trimStart(':', '：')
            if (inline.isNotBlank()) return inline.take(20)
        }
    }
    // Some cards only print the bureau name; accept it as a weak match.
    texts.firstOrNull { it.endsWith("公安局") || it.endsWith("分局") }?.let { return it.take(20) }
    val labelIndex = texts.indexOfFirst { it.contains("签发机关") }
    if (labelIndex >= 0) {
        texts.getOrNull(labelIndex + 1)?.let { return it.take(20) }
    }
    return ""
}

private fun toValidityDate(yearText: String, monthText: String, dayText: String): String? {
    val year = yearText.toIntOrNull() ?: return null
    val month = monthText.toIntOrNull() ?: return null
    val day = dayText.toIntOrNull() ?: return null
    if (year !in 1950..2100 || month !in 1..12 || day !in 1..31) return null
    return String.format(Locale.US, "%04d.%02d.%02d", year, month, day)
}

/**
 * The dash is frequently misread as `一`/`—`/`至` or dropped entirely, so the
 * dates are located first and the separator is not required. "长期" only has
 * one date.
 */
private fun extractValidity(joined: String): Pair<String, String>? {
    if (joined.isBlank()) return null
    val dates = datePattern.findAll(joined)
        .mapNotNull { toValidityDate(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        .toList()
    return when {
        dates.size >= 2 && dates[0] <= dates[1] -> dates[0] to dates[1]
        dates.size >= 2 -> dates[1] to dates[0]
        dates.size == 1 && joined.contains("长期") -> dates[0] to "长期"
        else -> null
    }
}

/** Parses the national-emblem side; null when neither issuer nor validity was read. */
fun parseIdCardBack(lines: List<OcrLine>): IdCardBack? {
    val ordered = lines
        .filter { it.text.isNotBlank() }
        .sortedBy { it.top }
    if (ordered.isEmpty()) return null
    val texts = ordered.map { it.text.stripSpaces() }
    val issuer = extractIssuer(texts)
    val validity = extractValidity(texts.joinToString(""))
    if (issuer.isBlank() && validity == null) return null
    return IdCardBack(
        issuer = issuer,
        validFrom = validity?.first.orEmpty(),
        validTo = validity?.second.orEmpty(),
    )
}
