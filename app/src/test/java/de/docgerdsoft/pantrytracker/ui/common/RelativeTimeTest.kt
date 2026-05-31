package de.docgerdsoft.pantrytracker.ui.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// Robolectric (JVM, default en-US locale) so the <plurals> resources resolve —
// RelativeTime.format now interpolates counts through the platform plural
// selector (#168 i18n) and therefore needs a Context. The asserted English
// strings are byte-identical to the pre-i18n hardcoded output.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelativeTimeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.fromEpochSeconds(1_000_000)
    private fun back(d: kotlin.time.Duration) = now - d
    private fun fmt(then: Instant) = RelativeTime.format(context, then, now)

    @Test fun justNow_under60s() {
        assertEquals("just now", fmt(back(0.seconds)))
        assertEquals("just now", fmt(back(59.seconds)))
    }

    @Test fun minutes_from60sTo59min() {
        assertEquals("1 minute ago", fmt(back(60.seconds)))
        assertEquals("1 minute ago", fmt(back(1.minutes)))
        assertEquals("2 minutes ago", fmt(back(2.minutes)))
        assertEquals("59 minutes ago", fmt(back(59.minutes)))
    }

    @Test fun hours_from60minTo23h() {
        assertEquals("1 hour ago", fmt(back(60.minutes)))
        assertEquals("23 hours ago", fmt(back(23.hours)))
    }

    @Test fun days_from24hTo6d() {
        assertEquals("1 day ago", fmt(back(24.hours)))
        assertEquals("6 days ago", fmt(back(6.days)))
    }

    @Test fun weeks_from7dTo27d() {
        assertEquals("1 week ago", fmt(back(7.days)))
        assertEquals("3 weeks ago", fmt(back(21.days)))
    }

    @Test fun months_from28dOnward() {
        // 28-day boundary quirk: 28/30 integer-divides to 0 — acceptable for v1.
        assertEquals("0 months ago", fmt(back(28.days)))
        assertEquals("1 month ago", fmt(back(30.days)))
        assertEquals("6 months ago", fmt(back(180.days)))
    }

    @Test fun negativeDelta_returnsJustNow() {
        // future timestamp (clock drift) — don't render "-1 minutes ago".
        assertEquals("just now", fmt(now + 5.seconds))
    }
}
