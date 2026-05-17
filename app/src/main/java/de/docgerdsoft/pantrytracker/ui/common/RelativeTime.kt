package de.docgerdsoft.pantrytracker.ui.common

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant

/** Bucketed human-readable relative time. "just now" if delta < 60s or
 *  negative (clock drift); coarser buckets up to "N months ago" (~30d).
 *  Pure function — no Android dependencies; fully testable. */
object RelativeTime {
    fun format(then: Instant, now: Instant): String {
        val delta = now - then
        return when {
            delta < 60.seconds -> "just now"
            delta < 60.minutes -> pluralize(delta.inWholeMinutes.toInt(), "minute")
            delta < 24.hours -> pluralize(delta.inWholeHours.toInt(), "hour")
            delta < 7.days -> pluralize(delta.inWholeDays.toInt(), "day")
            delta < 28.days -> pluralize((delta.inWholeDays / 7).toInt(), "week")
            else -> pluralize((delta.inWholeDays / 30).toInt(), "month")
        }
    }

    private fun pluralize(n: Int, unit: String): String =
        if (n == 1) "1 $unit ago" else "$n ${unit}s ago"
}
