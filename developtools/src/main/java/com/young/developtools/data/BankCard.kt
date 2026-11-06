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

/**
 * Extracts every Luhn-valid 12–19 digit candidate from [text]. OCR often
 * splits the number across lines/blocks, so the full text is also tried
 * joined as one digit stream. Longest-first, de-duplicated.
 */
fun parseBankCards(text: String): List<BankCard> {
    if (text.isBlank()) return emptyList()
    val candidates = mutableListOf<String>()
    digitRun.findAll(text).forEach { candidates += it.value.filter(Char::isDigit) }
    candidates += text.filter(Char::isDigit)

    return candidates
        .filter { it.length in 12..19 && isLuhnValid(it) }
        .distinct()
        .sortedByDescending { it.length }
        .map { number ->
            val scheme = schemeFor(number)
            BankCard(number = number, scheme = scheme, formatted = formatBankCardNumber(number, scheme))
        }
}

/** First (longest) Luhn-valid card in [text], or null. Live scan uses this. */
fun firstBankCard(text: String): BankCard? = parseBankCards(text).firstOrNull()
