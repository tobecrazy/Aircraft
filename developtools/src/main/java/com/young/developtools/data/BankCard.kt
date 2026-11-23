package com.young.developtools.data

/**
 * Bank-card number parsing over ML Kit OCR text. Pure functions so they stay
 * unit-testable without an Activity or the ML Kit model.
 *
 * On-device only: callers must not persist or upload the result.
 */
enum class BankCardScheme { VISA, MASTERCARD, AMEX, DINERS, DISCOVER, UNIONPAY, UNKNOWN }

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
    BankCardScheme.DINERS -> "Diners Club"
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
    number.startsWith("36") || number.startsWith("38") ||
        number.take(3).toIntOrNull()?.let { it in 300..305 } == true ->
        BankCardScheme.DINERS
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

/** Any digit run, with no length floor; used only for diagnostics. */
private val looseDigitRun = Regex("""\d[\d\s-]*""")

/**
 * Short digit fragment: one or more digits optionally separated by spaces or
 * dashes. A single digit counts — the tail of "300" misread as "3OO" leaves
 * just "3", and dropping it loses the last three card digits.
 */
private val digitFragment = Regex("""\d[\d\s-]*\d|\d""")
private const val MIN_FRAGMENT_DIGITS = 1
/**
 * Max gap characters between two fragments that can be stitched.
 *
 * ML Kit on embossed cards sometimes inserts a short word or several
 * misread characters between two digit groups. A gap of 8 covers
 * gaps like "300 X 6201" (3 non-digit chars) up to cases where a
 * Chinese character or a small label sits between digit clusters.
 */
private const val MAX_STITCH_GAP = 8

/**
 * Folds the characters ML Kit emits for a printed number onto the ASCII the run
 * patterns above expect: full-width forms (the bundled Chinese recognizer's
 * default output) and the Unicode spaces Java's `\s` does not cover. Without
 * this a correctly read number can produce no digit run at all.
 */
internal fun normalizeOcrDigits(text: String): String = buildString(text.length) {
    for (ch in text) {
        when {
            ch in '\uFF01'..'\uFF5E' -> append(ch - 0xFEE0)
            ch == '\u3000' || ch == '\u00A0' || ch == '\u2007' || ch == '\u202F' -> append(' ')
            else -> append(ch)
        }
    }
}

/**
 * Length of the longest digit run in [text] before the 12-digit acceptance
 * floor. A card read as two 8-digit fragments reports 8 here and 0 from
 * [cardNumberRuns], which separates "the number is being read but broken" from
 * "the crop holds no digits at all".
 */
internal fun longestDigitRun(text: String): Int {
    if (text.isBlank()) return 0
    return looseDigitRun.findAll(normalizeOcrDigits(text))
        .maxOfOrNull { run -> run.value.count(Char::isDigit) } ?: 0
}

// Letter-to-digit confusions ML Kit emits inside card numbers: O/l from
// embossed digits, B/S/G/Z from contour confusion. Included in the run pattern
// so a misread inside the number doesn't split it into sub-12-digit fragments.
private val digitConfusions = mapOf(
    'O' to '0', 'o' to '0', 'Q' to '0', 'D' to '0',
    'l' to '1', 'I' to '1', '|' to '1',
    'Z' to '2', 'S' to '5', 'B' to '8', 'b' to '6', 'G' to '6',
)

private fun String.normalizeOcrDigitConfusions(): String =
    map { digitConfusions[it] ?: it }.joinToString("")

private val CONFUSION_CHARS = digitConfusions.keys.joinToString("")
private val digitRunWithConfusions = Regex(
    """(?<![A-Za-z0-9])[\d$CONFUSION_CHARS][\d$CONFUSION_CHARS\s-]{10,}[\d$CONFUSION_CHARS](?![A-Za-z0-9])"""
)
/**
 * Broader fragment pattern that also spans known OCR confusion characters.
 * After confusion normalization the characters become digits, so the
 * fragment may be longer than the pure-digit one.
 */
private val digitFragmentWithConfusions = Regex(
    """[\d$CONFUSION_CHARS][\d$CONFUSION_CHARS\s-]*[\d$CONFUSION_CHARS]|[\d$CONFUSION_CHARS]"""
)

/** Lengths a scheme actually issues; used to reject windows inside a long blob. */
private val canonicalLengths = mapOf(
    BankCardScheme.VISA to setOf(13, 16, 19),
    BankCardScheme.MASTERCARD to setOf(16),
    BankCardScheme.AMEX to setOf(15),
    BankCardScheme.DINERS to setOf(14, 16, 17, 18, 19),
    BankCardScheme.DISCOVER to setOf(16, 19),
    BankCardScheme.UNIONPAY to setOf(16, 17, 18, 19),
)

/** A window with a valid known-scheme length, or a 16–19 digit unknown IIN. */
private fun looksLikeCard(number: String): Boolean {
    val scheme = schemeFor(number)
    return scheme != BankCardScheme.UNKNOWN && number.length in canonicalLengths.getValue(scheme)
}

private fun hasAcceptedLength(number: String): Boolean =
    when (val scheme = schemeFor(number)) {
        BankCardScheme.UNKNOWN -> number.length in 16..19
        else -> number.length in canonicalLengths.getValue(scheme)
    }

/**
 * Window helper so a long digit run doesn't have to be sliced to exactly one
 * length: e.g. a 20-digit OCR blob may contain the real 16-digit card plus
 * trailing expiry digits; sliding windows of every valid length surface it.
 *
 * Every window is Luhn-validated, but Luhn alone is far too weak here:
 * a 20-digit blob yields many passing windows (12, 14, 16 and 19 digits all
 * passed on "42424242424242429999"), and longest-first then ranks a bogus
 * 19-digit window above the real card. So windows are additionally required to
 * carry a known scheme prefix at a length that scheme issues. Unknown IINs
 * retain the 16–19 digit Luhn fallback.
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
    return luhnValid.filter(::hasAcceptedLength)
}

/**
 * Stitches adjacent short digit fragments on each line of [text] into
 * card-length candidates. Only activated when the main digit-run paths
 * found nothing, so it cannot shadow a clean reading with a noisy one.
 *
 * ML Kit frequently splits an embossed number on a complex background
 * into two or three fragments separated by one misread character. This
 * function walks every line, finds digit-bearing fragments, and joins
 * neighbouring ones whose gap is at most [MAX_STITCH_GAP] characters.
 * The merged digits are then windowed and Luhn-validated exactly like a
 * long blob.
 *
 * Three strategies run per line, stopping at the first that produces a
 * candidate:
 * 1. Pure digit fragments stitched together.
 * 2. Confusion-aware fragments (O→0, l→1, etc.) stitched together —
 *    this widens each fragment to include confusion characters that
 *    would be digits, so "62226201" + "OO38O83" becomes a single run.
 * 3. The gap between fragments is bridged by treating intervening
 *    confusion characters as digits — a last resort that covers cases
 *    where the gap itself is a misread digit, not just a separator.
 *
 * Fragments are processed per-line so digits on separate text lines
 * (e.g. card number vs. expiry date) are never accidentally joined.
 */
internal fun stitchFragments(text: String): List<String> {
    val results = mutableListOf<String>()
    for (line in text.split('\n')) {
        if (line.isBlank()) continue
        val normalized = normalizeOcrDigits(line)

        // Strategy 1: stitch pure digit fragments.
        results += stitchLine(normalized, digitFragment, filterDigits = true)
        if (results.isNotEmpty()) continue

        // Strategy 2: stitch confusion-aware fragments where confusion
        // characters inside each fragment are mapped to digits.
        results += stitchLine(normalized, digitFragmentWithConfusions, filterDigits = false)
        if (results.isNotEmpty()) continue

        // Strategy 3: bridge small gaps by treating intervening confusion
        // characters as potential digits. The whole gap between two
        // fragments is confusion-normalized, so "6222X0038" where X is a
        // non-confusion letter still breaks the stitch, but "6222O0038"
        // where O→0 produces "622200038".
        results += stitchLineWithGapRecovery(normalized)
    }
    return results.distinct()
}

/**
 * Core stitching: finds fragments via [pattern], merges adjacent ones whose
 * gap is ≤ [MAX_STITCH_GAP], extracts digits, and validates via Luhn.
 */
private fun stitchLine(
    line: String,
    pattern: Regex,
    filterDigits: Boolean,
): List<String> {
    val results = mutableListOf<String>()
    val fragments = pattern.findAll(line)
        .filter { it.value.count(Char::isDigit) >= MIN_FRAGMENT_DIGITS ||
            (!filterDigits && it.value.normalizeOcrDigitConfusions().count(Char::isDigit) >= MIN_FRAGMENT_DIGITS) }
        .toList()
    if (fragments.size < 2) return emptyList()
    var i = 0
    while (i < fragments.size) {
        val merged = StringBuilder()
        val extractDigits: (String) -> String = if (filterDigits) {
            { it.filter(Char::isDigit) }
        } else {
            { it.normalizeOcrDigitConfusions().filter(Char::isDigit) }
        }
        merged.append(extractDigits(fragments[i].value))
        var endIndex = fragments[i].range.last
        var j = i + 1
        while (j < fragments.size) {
            val gap = fragments[j].range.first - endIndex - 1
            if (gap > MAX_STITCH_GAP) break
            merged.append(extractDigits(fragments[j].value))
            endIndex = fragments[j].range.last
            j++
        }
        if (j > i + 1) { // at least two fragments were merged
            val digits = merged.toString()
            results += validateStitched(digits)
        }
        i++
    }
    return results
}

/**
 * Last-resort stitching: takes the text between fragment end and next
 * fragment start, confusion-normalizes it, and includes any resulting
 * digits. This recovers a gap character that is itself a misread digit
 * (e.g., "O" between two digit groups that should be "0").
 */
private fun stitchLineWithGapRecovery(line: String): List<String> {
    val results = mutableListOf<String>()
    val fragments = digitFragment.findAll(line)
        .filter { it.value.count(Char::isDigit) >= MIN_FRAGMENT_DIGITS }
        .toList()
    if (fragments.size < 2) return emptyList()
    var i = 0
    while (i < fragments.size) {
        val merged = StringBuilder()
        merged.append(fragments[i].value.filter(Char::isDigit))
        var endIndex = fragments[i].range.last
        var j = i + 1
        while (j < fragments.size) {
            val gapStart = endIndex + 1
            val gapEnd = fragments[j].range.first
            if (gapEnd - gapStart > MAX_STITCH_GAP) break
            // Confusion-normalize gap characters and include any resulting digits.
            if (gapStart < gapEnd) {
                val gapText = line.substring(gapStart, gapEnd)
                val recoveredDigits = gapText.normalizeOcrDigitConfusions().filter(Char::isDigit)
                merged.append(recoveredDigits)
            }
            merged.append(fragments[j].value.filter(Char::isDigit))
            endIndex = fragments[j].range.last
            j++
        }
        if (j > i + 1) {
            val digits = merged.toString()
            results += validateStitched(digits)
        }
        i++
    }
    return results
}

/**
 * Validates a stitched digit string: exact match, blob windowing, or
 * single-digit repair when one misread position breaks the Luhn checksum.
 */
private fun validateStitched(digits: String): List<String> {
    if (digits.length in 12..19) {
        if (isLuhnValid(digits) && hasAcceptedLength(digits)) return listOf(digits)
        // Also try blob windowing — a prefix or suffix may be an expiry
        // fragment that shouldn't be part of the card number.
        return blobCandidates(digits)
    }
    if (digits.length > 19) {
        return blobCandidates(digits)
    }
    return emptyList()
}

/**
 * Finds the single-digit change that makes [digits] Luhn-valid at a known
 * scheme length. Returns the repaired number, or null when no single
 * substitution produces a plausible card.
 *
 * Multiple positions may yield a Luhn-valid result, so a known-scheme
 * match (prefix + canonical length) is preferred over an unknown-IIN
 * fallback. Without this priority a repair at position 0 would change the
 * scheme prefix and win by iteration order alone.
 */
// Luhn single-digit repair removed: an ambiguous single reading cannot
// distinguish which position was misread. Cross-pass voting is the
// correct path and is handled by voteCardReadings / BankCardRotationConfirmer.

/**
 * Extracts Luhn-valid card candidates from [text], enforcing known scheme lengths
 * while retaining a 16–19 digit fallback for unknown IINs. OCR often splits the
 * number across lines, so a candidate may span spaces, dashes and newlines within
 * one digit run. The whole text must NOT be joined into one digit stream: digits
 * elsewhere (e.g. a CVV) could be glued onto the card and lock the wrong number.
 * Longest-first, de-duplicated.
 */
fun parseBankCards(text: String): List<BankCard> {
    if (text.isBlank()) return emptyList()
    val readable = normalizeOcrDigits(text)
    val rawCandidates = mutableListOf<String>()
    digitRun.findAll(readable).forEach { rawCandidates += it.value.filter(Char::isDigit) }
    // Pass 1: exact digit runs (covers clean OCR).
    val rawValid = mutableListOf<String>()
    rawCandidates.forEach { run ->
        if (run.length in 12..19) {
            if (isLuhnValid(run) && hasAcceptedLength(run)) rawValid += run
        } else if (run.length > 19) {
            // Blob longer than a card: window it; the Luhn gate rejects noise.
            rawValid += blobCandidates(run)
        }
    }
    // Pass 2: the same runs with OCR confusion letters mapped back to digits.
    // Only map confusions in standalone card-like runs, avoiding adjacent labels.
    val normalizedCandidates = mutableListOf<String>()
    digitRunWithConfusions.findAll(readable).forEach {
        val normalized = it.value.normalizeOcrDigitConfusions().filter(Char::isDigit)
        if (normalized.length in 12..19) {
            if (isLuhnValid(normalized) && hasAcceptedLength(normalized)) normalizedCandidates += normalized
        } else if (normalized.length > 19) {
            normalizedCandidates += blobCandidates(normalized)
        }
    }
    // Pass 3: stitch adjacent short fragments that Pass 1/2 missed. Only runs
    // when no clean candidate was found, so a noisy stitch never shadows the
    // primary path.
    if (rawValid.isEmpty() && normalizedCandidates.isEmpty()) {
        rawValid += stitchFragments(text)
    }
    val selected = (rawValid + normalizedCandidates)
        .distinct()
        .sortedWith(compareByDescending<String> { looksLikeCard(it) }.thenByDescending { it.length })

    return selected.map { number ->
            val scheme = schemeFor(number)
            BankCard(number = number, scheme = scheme, formatted = formatBankCardNumber(number, scheme))
        }
}

/** First (longest) Luhn-valid card in [text], or null. Live scan uses this. */
fun firstBankCard(text: String): BankCard? = parseBankCards(text).firstOrNull()

/** Card-length digit runs in one OCR result, before any checksum gate. */
internal fun cardNumberRuns(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val readable = normalizeOcrDigits(text)
    val runs = mutableListOf<String>()
    digitRun.findAll(readable).forEach { run ->
        val digits = run.value.filter(Char::isDigit)
        if (digits.length in 12..19) runs += digits
    }
    digitRunWithConfusions.findAll(readable).forEach {
        val digits = it.value.normalizeOcrDigitConfusions().filter(Char::isDigit)
        if (digits.length in 12..19) runs += digits
    }
    // Include stitched fragments so the vote mechanism can accumulate readings
    // from passes where the number was broken into short pieces. Stitching
    // may produce validated candidates, but here we only need card-length
    // runs (no Luhn gate) so the vote can decide.
    if (runs.isEmpty()) {
        for (line in text.split('\n')) {
            if (line.isBlank()) continue
            val normalized = normalizeOcrDigits(line)
            stitchRunsFromLine(normalized, digitFragment, filterDigits = true, runs)
            if (runs.isEmpty()) {
                stitchRunsFromLine(normalized, digitFragmentWithConfusions, filterDigits = false, runs)
            }
            if (runs.isEmpty()) {
                stitchRunsFromLineWithGapRecovery(normalized, runs)
            }
        }
    }
    return runs.distinct()
}

/** Stitches fragments on one line using [pattern] and appends card-length runs (no Luhn gate). */
private fun stitchRunsFromLine(
    line: String,
    pattern: Regex,
    filterDigits: Boolean,
    out: MutableList<String>,
) {
    val extractDigits: (String) -> String = if (filterDigits) {
        { it.filter(Char::isDigit) }
    } else {
        { it.normalizeOcrDigitConfusions().filter(Char::isDigit) }
    }
    val fragments = pattern.findAll(line)
        .filter { extractDigits(it.value).length >= MIN_FRAGMENT_DIGITS }
        .toList()
    if (fragments.size < 2) return
    var i = 0
    while (i < fragments.size) {
        val merged = StringBuilder()
        merged.append(extractDigits(fragments[i].value))
        var endIndex = fragments[i].range.last
        var j = i + 1
        while (j < fragments.size) {
            val gap = fragments[j].range.first - endIndex - 1
            if (gap > MAX_STITCH_GAP) break
            merged.append(extractDigits(fragments[j].value))
            endIndex = fragments[j].range.last
            j++
        }
        if (j > i + 1) {
            val digits = merged.toString()
            if (digits.length in 12..19) out += digits
        }
        i++
    }
}

/** Stitches fragments on one line, recovering confusion chars in gaps. */
private fun stitchRunsFromLineWithGapRecovery(line: String, out: MutableList<String>) {
    val fragments = digitFragment.findAll(line)
        .filter { it.value.count(Char::isDigit) >= MIN_FRAGMENT_DIGITS }
        .toList()
    if (fragments.size < 2) return
    var i = 0
    while (i < fragments.size) {
        val merged = StringBuilder()
        merged.append(fragments[i].value.filter(Char::isDigit))
        var endIndex = fragments[i].range.last
        var j = i + 1
        while (j < fragments.size) {
            val gapStart = endIndex + 1
            val gapEnd = fragments[j].range.first
            if (gapEnd - gapStart > MAX_STITCH_GAP) break
            if (gapStart < gapEnd) {
                val gapText = line.substring(gapStart, gapEnd)
                merged.append(gapText.normalizeOcrDigitConfusions().filter(Char::isDigit))
            }
            merged.append(fragments[j].value.filter(Char::isDigit))
            endIndex = fragments[j].range.last
            j++
        }
        if (j > i + 1) {
            val digits = merged.toString()
            if (digits.length in 12..19) out += digits
        }
        i++
    }
}

/**
 * Per-position majority over several readings of one card number, then a Luhn
 * re-check.
 *
 * A 19-digit embossed number is long enough that one substituted glyph fails
 * the checksum and [parseBankCards] throws the whole reading away, even though
 * the passes usually misread *different* positions. A bare per-position
 * majority would also average unrelated runs of the same length into a number
 * no pass ever read, so the repair is fenced in four ways:
 *
 * - readings are clustered by Hamming distance and only the largest cluster is
 *   voted on, so an unrelated run of the same length never takes part;
 * - the cluster needs [MIN_VOTE_READINGS] members, so a couple of mutually
 *   different near misses cannot average into a plausible number;
 * - every position needs a strict majority of the cluster;
 * - the merged number must still pass Luhn and the scheme-length gate.
 */
internal fun voteCardReadings(readings: List<String>): String? =
    readings.filter { it.length in 12..19 }
        .groupBy { it.length }
        .entries
        .sortedByDescending { it.key }
        .firstNotNullOfOrNull { (_, sameLength) -> voteSameLength(largestCluster(sameLength)) }

/** The biggest group of readings that differ only in a couple of glyphs. */
private fun largestCluster(readings: List<String>): List<String> {
    val clusters = mutableListOf<MutableList<String>>()
    for (reading in readings) {
        val cluster = clusters.firstOrNull { existing ->
            hammingDistance(existing.first(), reading) <= MAX_VOTE_DISTANCE
        }
        if (cluster != null) cluster += reading else clusters += mutableListOf(reading)
    }
    return clusters.maxByOrNull { it.size }.orEmpty()
}

private fun hammingDistance(left: String, right: String): Int {
    if (left.length != right.length) return Int.MAX_VALUE
    return left.indices.count { left[it] != right[it] }
}

private fun voteSameLength(readings: List<String>): String? {
    if (readings.size < MIN_VOTE_READINGS) return null
    val merged = StringBuilder(readings.first().length)
    for (position in 0 until readings.first().length) {
        val winner = readings.map { it[position] }
            .groupingBy { it }
            .eachCount()
            .entries
            .maxByOrNull { it.value } ?: return null
        if (winner.value * 2 <= readings.size) return null
        merged.append(winner.key)
    }
    val number = merged.toString()
    // The repair may only be a small correction of every reading it came from, which
    // also stops a chained cluster from drifting onto a number none of them is near.
    if (readings.any { hammingDistance(it, number) > MAX_VOTE_DISTANCE }) return null
    return if (isLuhnValid(number) && hasAcceptedLength(number)) number else null
}

private const val MIN_VOTE_READINGS = 4

/** Glyphs a cluster may disagree on; a real second number differs in far more. */
private const val MAX_VOTE_DISTANCE = 2
