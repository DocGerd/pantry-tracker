package de.docgerdsoft.pantrytracker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeTextTest {

    // ---- sanitizeBarcode ----

    @Test
    fun sanitize_keepsDigits() {
        assertEquals("5449000000996", "5449000000996".sanitizeBarcode())
    }

    @Test
    fun sanitize_keepsPrintableLettersAndSymbols() {
        // sanitize handles control chars + RTL overrides only; format validation
        // (digits-only, length) is the validator's job at the OffApiClient boundary.
        assertEquals("foo?token=bar", "foo?token=bar".sanitizeBarcode())
    }

    @Test
    fun sanitize_stripsAllRtlOverrides() {
        // U+202A LRE, U+202B RLE, U+202D LRO, U+202E RLO, U+2066..U+2069
        val input = "\u202a1\u202b2\u202d3\u202e4\u20665\u20676\u20687\u20698"
        assertEquals("12345678", input.sanitizeBarcode())
    }

    @Test
    fun sanitize_stripsNewlineAndCr() {
        assertEquals("54490996", "5449\n09\r96".sanitizeBarcode())
    }

    @Test
    fun sanitize_stripsNulByte() {
        assertEquals("54490996", "5449\u00000996".sanitizeBarcode())
    }

    @Test
    fun sanitize_stripsAllC0Range() {
        // C0 = U+0000..U+001F. Spot-check the corners and a few inside (NUL, BEL,
        // ESC, US).
        val input = "a\u0000b\u0007c\u001bd\u001fe"
        assertEquals("abcde", input.sanitizeBarcode())
    }

    @Test
    fun sanitize_stripsDel() {
        // DEL = U+007F
        assertEquals("abc", "a\u007fb\u007fc".sanitizeBarcode())
    }

    @Test
    fun sanitize_stripsC1Range() {
        // C1 = U+0080..U+009F (bottom, middle, top of range).
        val input = "a\u0080b\u0090c\u009fd"
        assertEquals("abcd", input.sanitizeBarcode())
    }

    @Test
    fun sanitize_capsAt32Chars() {
        val input = "1".repeat(33)
        val out = input.sanitizeBarcode()
        assertEquals(32, out.length)
        assertEquals("1".repeat(32), out)
    }

    @Test
    fun sanitize_capAppliesAfterStripping() {
        // 33 digits with a control-char prefix: strip first drops the control
        // (33 digits left), then truncates to 32. A naive cap-then-strip impl
        // would yield 31.
        val input = "\u0000" + "1".repeat(33)
        assertEquals(32, input.sanitizeBarcode().length)
    }

    @Test
    fun sanitize_emptyReturnsEmpty() {
        assertEquals("", "".sanitizeBarcode())
    }

    @Test
    fun sanitize_onlyControlCharsReturnsEmpty() {
        assertEquals("", "\u0000\u202e\n\r\u007f".sanitizeBarcode())
    }

    @Test
    fun sanitize_keepsRegularSpace() {
        // U+0020 is printable, not a control char. Validator rejects it later;
        // sanitizer leaves it alone so the hostile input is observable downstream
        // rather than silently massaged into looking valid.
        assertEquals("foo bar", "foo bar".sanitizeBarcode())
    }

    // ---- barcodeHint ----

    @Test
    fun hint_ean13_returnsFourEllipsisTwo() {
        assertEquals("5449\u202696", "5449000000996".barcodeHint())
    }

    @Test
    fun hint_ean8_returnsFourEllipsisTwo() {
        // EAN-8 is 8 digits, long enough that 4+2 doesn't overlap.
        assertEquals("4006\u202602", "40063202".barcodeHint())
    }

    @Test
    fun hint_exactlyMinThreshold_returnsHint() {
        // length == 7 is the threshold: take(4)+ellipsis+takeLast(2) reveals
        // 6 of 7 chars (the middle one stays hidden). Shorter inputs would overlap.
        assertEquals("1234\u202667", "1234567".barcodeHint())
    }

    @Test
    fun hint_belowThreshold_returnsPlaceholder() {
        // For very short barcodes the hint would reveal the whole string;
        // collapse to a placeholder so log output never leaks it.
        assertEquals("<short>", "12345".barcodeHint())
        assertEquals("<short>", "abc".barcodeHint())
    }

    @Test
    fun hint_emptyReturnsPlaceholder() {
        assertEquals("<short>", "".barcodeHint())
    }
}
