package com.young.aircraft.data

private val emailPattern = Regex(
    """^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$"""
)

internal fun isValidOptionalEmail(input: String): Boolean =
    input.isBlank() || emailPattern.matches(input.trim())
