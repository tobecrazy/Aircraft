package com.young.developtools.viewmodel

/**
 * Deterministic, local validation for a card number candidate.
 *
 * The on-device model is treated as untrusted: a candidate is accepted only
 * when it is 12-19 digits, passes the Luhn checksum, and its digits occur in
 * the OCR text that produced it. No issuer or scheme detection is performed.
 */
object CardNumberValidator {

    const val MIN_DIGITS = 12
    const val MAX_DIGITS = 19

    /** Keeps ASCII digits only, so spaces and hyphens act as separators. */
    fun digitsOf(text: String): String = text.filter { it in '0'..'9' }

    fun hasValidLength(digits: String): Boolean = digits.length in MIN_DIGITS..MAX_DIGITS

    /** Standard Luhn checksum over a digit-only string. */
    fun passesLuhn(digits: String): Boolean {
        if (digits.isEmpty()) return false
        var sum = 0
        var doubleDigit = false
        for (index in digits.indices.reversed()) {
            val char = digits[index]
            if (char !in '0'..'9') return false
            var value = char - '0'
            if (doubleDigit) {
                value *= 2
                if (value > 9) value -= 9
            }
            sum += value
            doubleDigit = !doubleDigit
        }
        return sum % 10 == 0
    }

    /**
     * Returns the normalized PAN when [candidate] is a plausible card number
     * that really appears in [ocrText]; null otherwise.
     */
    fun validate(ocrText: String, candidate: String?): String? {
        val digits = digitsOf(candidate ?: return null)
        if (!hasValidLength(digits)) return null
        if (!passesLuhn(digits)) return null
        if (!digitsOf(ocrText).contains(digits)) return null
        return digits
    }

    /**
     * Finds the first locally valid PAN in [ocrText] without involving the
     * model. Used when the on-device AI model or structured output is
     * unavailable, and as a deterministic baseline for tests.
     */
    fun extract(ocrText: String): String? {
        return CARD_CANDIDATE.findAll(ocrText)
            .mapNotNull { validate(ocrText, it.value) }
            .firstOrNull()
    }

    /** 12-19 digits, optionally separated by spaces or hyphens. */
    private val CARD_CANDIDATE = Regex("""(?<!\d)(?:\d[ -]*){11,18}\d(?!\d)""")

    /**
     * Masks every digit except the trailing group, keeping 4-digit grouping so
     * the result still resembles a card number without exposing it.
     */
    fun mask(digits: String): String {
        val normalized = digitsOf(digits)
        if (normalized.isEmpty()) return ""
        val groups = normalized.chunked(4)
        val lastIndex = groups.lastIndex
        return groups.mapIndexed { index, group ->
            if (index == lastIndex) group else "•".repeat(group.length)
        }.joinToString(" ")
    }
}
