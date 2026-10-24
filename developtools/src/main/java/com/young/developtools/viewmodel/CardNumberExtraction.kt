package com.young.developtools.viewmodel

import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide

/**
 * Structured-output target for the Prompt API.
 *
 * Intentionally narrow: it carries only a found flag and a candidate PAN.
 * CVV, expiry date, cardholder name, and issuer must never be requested or
 * returned. The candidate is still untrusted and must pass
 * [CardNumberValidator.validate] before it reaches the UI.
 */
@Generable(description = "A payment card number extracted from OCR text.")
data class CardNumberExtraction(
    @Guide(description = "True only when a card number is present in the OCR text.")
    val found: Boolean,
    @Guide(
        description = "The 12-19 digit card number exactly as it appears in the OCR text, " +
            "spaces or hyphens allowed. Empty when no card number is present. " +
            "Never infer, complete, or invent digits."
    )
    val cardNumber: String
)
