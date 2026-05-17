package de.docgerdsoft.pantrytracker.ui.common

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = Instant.fromEpochSeconds(1_000_000)
    private fun back(d: kotlin.time.Duration) = now - d

    @Test fun justNow_under60s() {
        assertEquals("just now", RelativeTime.format(back(0.seconds), now))
        assertEquals("just now", RelativeTime.format(back(59.seconds), now))
    }

    @Test fun minutes_from60sTo59min() {
        assertEquals("1 minute ago", RelativeTime.format(back(60.seconds), now))
        assertEquals("1 minute ago", RelativeTime.format(back(1.minutes), now))
        assertEquals("2 minutes ago", RelativeTime.format(back(2.minutes), now))
        assertEquals("59 minutes ago", RelativeTime.format(back(59.minutes), now))
    }

    @Test fun hours_from60minTo23h() {
        assertEquals("1 hour ago", RelativeTime.format(back(60.minutes), now))
        assertEquals("23 hours ago", RelativeTime.format(back(23.hours), now))
    }

    @Test fun days_from24hTo6d() {
        assertEquals("1 day ago", RelativeTime.format(back(24.hours), now))
        assertEquals("6 days ago", RelativeTime.format(back(6.days), now))
    }

    @Test fun weeks_from7dTo27d() {
        assertEquals("1 week ago", RelativeTime.format(back(7.days), now))
        assertEquals("3 weeks ago", RelativeTime.format(back(21.days), now))
    }

    @Test fun months_from28dOnward() {
        // 28-day boundary quirk: 28/30 integer-divides to 0 — acceptable for v1.
        assertEquals("0 months ago", RelativeTime.format(back(28.days), now))
        assertEquals("1 month ago", RelativeTime.format(back(30.days), now))
        assertEquals("6 months ago", RelativeTime.format(back(180.days), now))
    }

    @Test fun negativeDelta_returnsJustNow() {
        // future timestamp (clock drift) — don't render "-1 minutes ago".
        assertEquals("just now", RelativeTime.format(now + 5.seconds, now))
    }
}
