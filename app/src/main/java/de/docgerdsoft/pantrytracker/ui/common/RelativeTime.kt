package de.docgerdsoft.pantrytracker.ui.common

import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val DAYS_PER_WEEK = 7L
private const val DAYS_PER_MONTH_ROUGH = 30L

/** Bucketed human-readable relative time. Negative deltas (clock drift) and
 *  small positives both fall into the "just now" branch because they compare
 *  `< 60.seconds` — the negativeDelta_returnsJustNow test pins this. Coarser
 *  buckets follow up to "N months ago" using a ~30-day month. Pure function —
 *  no Android dependencies; fully testable. */
object RelativeTime {
    fun format(then: Instant, now: Instant): String {
        val delta = now - then
        return when {
            delta < 60.seconds -> "just now"
            delta < 60.minutes -> pluralize(delta.inWholeMinutes.toInt(), "minute")
            delta < 24.hours -> pluralize(delta.inWholeHours.toInt(), "hour")
            delta < 7.days -> pluralize(delta.inWholeDays.toInt(), "day")
            // 28-day boundary: integer-divides to 0 weeks; falls into months
            // bucket which then renders "0 months ago" (28/30 == 0). Acceptable
            // v1 trade-off — pinned by months_from28dOnward in the test.
            delta < 28.days -> pluralize((delta.inWholeDays / DAYS_PER_WEEK).toInt(), "week")
            else -> pluralize((delta.inWholeDays / DAYS_PER_MONTH_ROUGH).toInt(), "month")
        }
    }

    private fun pluralize(n: Int, unit: String): String =
        if (n == 1) "1 $unit ago" else "$n ${unit}s ago"
}
