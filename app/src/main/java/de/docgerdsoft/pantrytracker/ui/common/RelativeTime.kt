package de.docgerdsoft.pantrytracker.ui.common

import android.content.Context
import de.docgerdsoft.pantrytracker.R
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val DAYS_PER_WEEK = 7L
private const val DAYS_PER_MONTH_ROUGH = 30L

/** Bucketed human-readable relative time.
 *
 *  Contract: a negative delta (clock drift, or a row whose `updatedAt` is briefly
 *  in the future because of an unsynced device clock) is treated as "just now"
 *  rather than something nonsensical like "in 3 hours". This is a deliberate
 *  guarantee, not an artifact of how the buckets cascade; the
 *  negativeDelta_returnsJustNow test pins it.
 *
 *  Buckets cascade up to "N months ago" using a ~30-day month. The strings live
 *  in `<plurals>` resources (SR/i18n #168) so the grammar is locale-correct —
 *  English collapses to one/other, German to its own one/other forms — and the
 *  count is interpolated by the platform's plural selector. A [Context] is
 *  required to resolve those resources. */
object RelativeTime {
    fun format(context: Context, then: Instant, now: Instant): String {
        val res = context.resources
        val delta = now - then
        return when {
            delta < 60.seconds -> context.getString(R.string.relative_just_now)
            delta < 60.minutes -> {
                val n = delta.inWholeMinutes.toInt()
                res.getQuantityString(R.plurals.relative_minutes, n, n)
            }
            delta < 24.hours -> {
                val n = delta.inWholeHours.toInt()
                res.getQuantityString(R.plurals.relative_hours, n, n)
            }
            delta < 7.days -> {
                val n = delta.inWholeDays.toInt()
                res.getQuantityString(R.plurals.relative_days, n, n)
            }
            // 28-day boundary: integer-divides to 0 weeks; falls into months
            // bucket which then renders "0 months ago" (28/30 == 0). Acceptable
            // v1 trade-off — pinned by months_from28dOnward in the test.
            delta < 28.days -> {
                val n = (delta.inWholeDays / DAYS_PER_WEEK).toInt()
                res.getQuantityString(R.plurals.relative_weeks, n, n)
            }
            else -> {
                val n = (delta.inWholeDays / DAYS_PER_MONTH_ROUGH).toInt()
                res.getQuantityString(R.plurals.relative_months, n, n)
            }
        }
    }
}
