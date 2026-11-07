package com.young.developtools.data

/**
 * Bank-card number parsing over ML Kit OCR text. Pure functions so they stay
 * unit-testable without an Activity or the ML Kit model.
 *
 * On-device only: callers must not persist or upload the result.
 */
enum class BankCardScheme { VISA, MASTERCARD, AMEX, DISCOVER, UNIONPAY, UNKNOWN }

data class BankCard(
    val number: String,
    val scheme: BankCardScheme,
    val formatted: String,
) {
    val last4: String get() = number.takeLast(4)
}

fun BankCardScheme.label(): String = when (this) {
    BankCardScheme.VISA -> "Visa"
    BankCardScheme.MASTERCARD -> "Mastercard"
    BankCardScheme.AMEX -> "American Express"
    BankCardScheme.DISCOVER -> "Discover"
    BankCardScheme.UNIONPAY -> "UnionPay"
    BankCardScheme.UNKNOWN -> "Unknown"
}

/** Groups in 4, except Amex which prints 4-6-5. */
fun formatBankCardNumber(number: String, scheme: BankCardScheme): String =
    if (scheme == BankCardScheme.AMEX && number.length == 15) {
        "${number.substring(0, 4)} ${number.substring(4, 10)} ${number.substring(10)}"
    } else {
        number.chunked(4).joinToString(" ")
    }

fun schemeFor(number: String): BankCardScheme = when {
    number.startsWith("62") -> BankCardScheme.UNIONPAY
    number.startsWith("4") -> BankCardScheme.VISA
    number.startsWith("5") || number.startsWith("2") -> BankCardScheme.MASTERCARD
    number.startsWith("34") || number.startsWith("37") -> BankCardScheme.AMEX
    number.startsWith("6") -> BankCardScheme.DISCOVER
    else -> BankCardScheme.UNKNOWN
}

fun isLuhnValid(number: String): Boolean {
    if (number.isEmpty() || !number.all { it.isDigit() }) return false
    var sum = 0
    var double = false
    for (i in number.length - 1 downTo 0) {
        var digit = number[i] - '0'
        if (double) {
            digit *= 2
            if (digit > 9) digit -= 9
        }
        sum += digit
        double = !double
    }
    return sum % 10 == 0
}

/** digit, space or dash run; requires at least 12 digits. */
private val digitRun = Regex("""\d[\d\s-]{10,}\d""")

// Letter-to-digit confusions ML Kit emits inside card numbers: O/l from
// embossed digits, B/S/G/Z from contour confusion. Included in the run pattern
// so a misread inside the number doesn't split it into sub-12-digit fragments.
private val digitConfusions = mapOf(
    'O' to '0', 'o' to '0', 'Q' to '0', 'D' to '0',
    'l' to '1', 'I' to '1', '|' to '1',
    'Z' to '2', 'S' to '5', 'B' to '8', 'G' to '6',
)

private fun String.normalizeOcrDigitConfusions(): String =
    map { digitConfusions[it] ?: it }.joinToString("")

private val CONFUSION_CHARS = digitConfusions.keys.joinToString("")
private val digitRunWithConfusions = Regex("""[\d$CONFUSION_CHARS][\d$CONFUSION_CHARS\s-]{10,}[\d$CONFUSION_CHARS]""")

/** Lengths a scheme actually issues; used to reject windows inside a long blob. */
private val canonicalLengths = mapOf(
    BankCardScheme.VISA to setOf(13, 16, 19),
    BankCardScheme.MASTERCARD to setOf(16),
    BankCardScheme.AMEX to setOf(15),
    BankCardScheme.DISCOVER to setOf(16, 19),
    BankCardScheme.UNIONPAY to setOf(16, 17, 18, 19),
)

/** A window of a long blob that both has a known IIN prefix and a real length. */
private fun looksLikeCard(number: String): Boolean {
    val scheme = schemeFor(number)
    return scheme != BankCardScheme.UNKNOWN && number.length in canonicalLengths.getValue(scheme)
}

/**
 * Window helper so a long digit run doesn't have to be sliced to exactly one
 * length: e.g. a 20-digit OCR blob may contain the real 16-digit card plus
 * trailing expiry digits; sliding windows of every valid length surface it.
 *
 * Every window length is Luhn-validated, but Luhn alone is far too weak here:
 * a 20-digit blob yields many passing windows (12, 14, 16 and 19 digits all
 * passed on "42424242424242429999"), and longest-first then ranks a bogus
 * 19-digit window above the real card. So windows are additionally required to
 * carry a known scheme prefix at a length that scheme issues; the plain Luhn
 * list is only a fallback for cards outside the known IIN ranges.
 */
private fun blobCandidates(digits: String): List<String> {
    if (digits.length < 12) return emptyList()
    val luhnValid = mutableListOf<String>()
    for (len in 12..19) {
        if (digits.length < len) continue
        for (start in 0..digits.length - len) {
            val window = digits.substring(start, start + len)
            if (isLuhnValid(window)) luhnValid += window
        }
    }
    val plausible = luhnValid.filter(::looksLikeCard)
    return plausible.ifEmpty { luhnValid }
}

/**
 * Extracts every Luhn-valid 12–19 digit candidate from [text]. OCR often
 * splits the number across lines, so a candidate may span spaces, dashes and
 * newlines within one digit run. The whole text must NOT be joined into one
 * digit stream: digits elsewhere (e.g. a CVV) would be glued onto the card
 * and, when that 12–19 digit blob Luhn-passes, sort longest-first ahead of
 * the real card and lock the wrong number. Longest-first, de-duplicated.
 */
fun parseBankCards(text: String): List<BankCard> {
    if (text.isBlank()) return emptyList()
    val rawCandidates = mutableListOf<String>()
    digitRun.findAll(text).forEach { rawCandidates += it.value.filter(Char::isDigit) }
    // Pass 1: exact digit runs (covers clean OCR).
    val rawValid = mutableListOf<String>()
    rawCandidates.forEach { run ->
        if (run.length in 12..19) {
            if (isLuhnValid(run)) rawValid += run
        } else if (run.length > 19) {
            // Blob longer than a card: window it; the Luhn gate rejects noise.
            rawValid += blobCandidates(run)
        }
    }
    // Pass 2: the same runs with OCR confusion letters mapped back to digits.
    // Normalized candidates may be shorter (a letter at the run edge maps to a
    // digit that the length check still counts), and may need windowing too.
    val normalizedCandidates = mutableListOf<String>()
    digitRunWithConfusions.findAll(text).forEach {
        val normalized = it.value.normalizeOcrDigitConfusions().filter(Char::isDigit)
        if (normalized.length in 12..19) {
            if (isLuhnValid(normalized)) normalizedCandidates += normalized
        } else if (normalized.length > 19) {
            normalizedCandidates += blobCandidates(normalized)
        }
    }
    val selected = (rawValid.ifEmpty { normalizedCandidates })
        .distinct()
        .sortedByDescending { it.length }

    return selected.map { number ->
            val scheme = schemeFor(number)
            BankCard(number = number, scheme = scheme, formatted = formatBankCardNumber(number, scheme))
        }
}

/** First (longest) Luhn-valid card in [text], or null. Live scan uses this. */
fun firstBankCard(text: String): BankCard? = parseBankCards(text).firstOrNull()
