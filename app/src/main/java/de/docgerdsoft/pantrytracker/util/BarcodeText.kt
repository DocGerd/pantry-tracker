package de.docgerdsoft.pantrytracker.util

/**
 * Boundary helpers for any [String] that originated as a barcode (scanner or
 * manual entry). Pure text transforms — no logging, no I/O — so they can be
 * called from any layer without introducing dependencies.
 *
 * - [sanitizeBarcode] is the input-boundary normalizer (SR-13): strips control
 *   chars + RTL overrides, length-caps. Does **not** validate format; the
 *   OffApiClient regex gate handles that.
 * - [barcodeHint] is the log-output redactor (SR-10/SR-11/SR-12): produces a
 *   short "5449…96"-style fingerprint that preserves diagnostic value without
 *   leaking the full code into logcat.
 */

private const val MAX_BARCODE_LENGTH = 32
private const val HINT_PREFIX = 4
private const val HINT_SUFFIX = 2
private const val HINT_MIN_LENGTH = 7

// ASCII / ISO-8859 control-char boundaries. Spelt out so call sites read as
// intent ("strip C0") rather than as numeric ranges, and so Detekt MagicNumber
// has names to point at.
private const val C0_END_EXCLUSIVE = 0x20 // U+0000..U+001F = C0 controls
private const val DEL = 0x7F // U+007F = delete
private const val C1_START = 0x80 // U+0080..U+009F = C1 controls
private const val C1_END = 0x9F

// Unicode bidirectional override codepoints. RTL overrides in Compose Text are
// a known display-spoofing vector (security review SR-13). U+202C (PDF, plain
// pop-direction) is omitted intentionally — it's a balanced terminator, not an
// override, and is harmless in isolation.
private val RTL_OVERRIDES = setOf(
    '‪', // LRE — Left-to-Right Embedding
    '‫', // RLE — Right-to-Left Embedding
    '‭', // LRO — Left-to-Right Override
    '‮', // RLO — Right-to-Left Override
    '⁦', // LRI — Left-to-Right Isolate
    '⁧', // RLI — Right-to-Left Isolate
    '⁨', // FSI — First Strong Isolate
    '⁩', // PDI — Pop Directional Isolate
)

/**
 * Strips C0 (U+0000..U+001F), DEL (U+007F), C1 (U+0080..U+009F), and the
 * bidirectional override codepoints, then truncates to 32 chars. Returns the
 * cleaned string unchanged in shape (no trimming, no case fold) so format
 * validation downstream sees what the user actually entered minus the
 * dangerous-but-invisible parts.
 */
fun String.sanitizeBarcode(): String =
    filter { c ->
        val code = c.code
        !(code < C0_END_EXCLUSIVE || code == DEL || code in C1_START..C1_END || c in RTL_OVERRIDES)
    }.take(MAX_BARCODE_LENGTH)

/**
 * Produces a redacted log fingerprint of the form `"5449…96"`. For inputs
 * shorter than 7 chars the prefix+suffix would overlap or leak the entire
 * string, so we return a placeholder instead.
 */
fun String.barcodeHint(): String {
    if (length < HINT_MIN_LENGTH) return "<short>"
    return "${take(HINT_PREFIX)}…${takeLast(HINT_SUFFIX)}"
}
