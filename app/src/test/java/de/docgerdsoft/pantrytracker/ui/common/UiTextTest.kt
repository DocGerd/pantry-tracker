package de.docgerdsoft.pantrytracker.ui.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.docgerdsoft.pantrytracker.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UiTextTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun raw_resolvesToItsValue() {
        assertEquals("disk full", UiText.Raw("disk full").resolve(context))
    }

    @Test
    fun res_noArgs_resolvesPlainString() {
        assertEquals("unknown error", UiText.Res(R.string.error_unknown_reason).resolve(context))
    }

    @Test
    fun res_withRawArg_formatsTemplate() {
        val text = UiText.Res(R.string.scan_error_save, listOf(UiText.Raw("disk full")))
        assertEquals("Couldn't save: disk full", text.resolve(context))
    }

    @Test
    fun res_withNestedResArg_resolvesFallbackThenFormats() {
        // The unknown-error fallback is itself a UiText.Res and must localize.
        val text = UiText.Res(
            R.string.error_read_inventory,
            listOf(UiText.Res(R.string.error_unknown_reason)),
        )
        assertEquals("Couldn't read inventory: unknown error", text.resolve(context))
    }
}
