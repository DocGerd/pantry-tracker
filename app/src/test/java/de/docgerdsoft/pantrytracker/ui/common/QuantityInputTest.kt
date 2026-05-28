package de.docgerdsoft.pantrytracker.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM coverage for [sanitizeQuantityInput] — the digit-filter + length-cap
 * applied to the quantity text field before it reaches the ViewModel.
 */
class QuantityInputTest {

    @Test
    fun stripsNonDigitCharacters() {
        assertEquals("123", sanitizeQuantityInput("1a2b3c"))
    }

    @Test
    fun dropsSignsDotsAndWhitespace() {
        assertEquals("12", sanitizeQuantityInput(" -1.2 "))
    }

    @Test
    fun emptyInput_staysEmpty() {
        assertEquals("", sanitizeQuantityInput(""))
    }

    @Test
    fun allNonDigits_collapseToEmpty() {
        assertEquals("", sanitizeQuantityInput("abc-.,"))
    }

    @Test
    fun capsAtFourDigits() {
        assertEquals("1234", sanitizeQuantityInput("123456789"))
    }

    @Test
    fun capAppliesAfterFilteringNotBefore() {
        // The cap must be on digits kept, not on the raw string: "9x9x9x9x9"
        // has nine digits interleaved with letters; we keep the first four.
        assertEquals("9999", sanitizeQuantityInput("9x9x9x9x9"))
    }

    @Test
    fun leadingZerosArePreserved() {
        // sanitize is purely lexical — numeric normalisation is the caller's job.
        assertEquals("0007", sanitizeQuantityInput("0007"))
    }
}
