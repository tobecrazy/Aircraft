package com.young.aircraft.data

private val mainlandPhonePattern = Regex(
    "(?:1[3-9]\\d{9}|0(?:10|2[0-9]|[3-9]\\d{2})[2-9]\\d{6,7})"
)
private val hongKongPhonePattern = Regex("(?:[2-3]\\d{7}|[4-9]\\d{7})")
private val macauPhonePattern = Regex("(?:2\\d{7}|6\\d{7})")
private val taiwanPhonePattern = Regex("(?:09\\d{8}|0[2-8]\\d{7,9})")
private val phoneFormattingPattern = Regex("[\\s()-]")

/** Accepts mainland China, Hong Kong, Macau, and Taiwan mobile or landline numbers. */
internal fun isValidChinaPhoneNumber(input: String): Boolean {
    val phone = input.trim().replace(phoneFormattingPattern, "")

    return when {
        phone.startsWith("+86") -> mainlandPhonePattern.matches(phone.removePrefix("+86"))
        phone.startsWith("0086") -> mainlandPhonePattern.matches(phone.removePrefix("0086"))
        phone.startsWith("+852") -> hongKongPhonePattern.matches(phone.removePrefix("+852"))
        phone.startsWith("00852") -> hongKongPhonePattern.matches(phone.removePrefix("00852"))
        phone.startsWith("+853") -> macauPhonePattern.matches(phone.removePrefix("+853"))
        phone.startsWith("00853") -> macauPhonePattern.matches(phone.removePrefix("00853"))
        phone.startsWith("+886") -> matchesTaiwanInternational(phone.removePrefix("+886"))
        phone.startsWith("00886") -> matchesTaiwanInternational(phone.removePrefix("00886"))
        phone.startsWith("+") || phone.startsWith("00") -> false
        else -> mainlandPhonePattern.matches(phone) ||
            hongKongPhonePattern.matches(phone) ||
            macauPhonePattern.matches(phone) ||
            taiwanPhonePattern.matches(phone)
    }
}

private fun matchesTaiwanInternational(phone: String): Boolean =
    taiwanPhonePattern.matches(phone) || taiwanPhonePattern.matches("0$phone")
