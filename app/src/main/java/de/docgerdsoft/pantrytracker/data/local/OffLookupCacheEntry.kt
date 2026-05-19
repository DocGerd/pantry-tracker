package de.docgerdsoft.pantrytracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

/**
 * Cached OFF lookup result for non-pantry barcodes (#48). Re-scans of an
 * already-resolved barcode hit this table instead of walking the OFF host
 * chain again. 30-day TTL enforced lazily by [ProductRepositoryImpl].
 *
 * Stores the **post-gating** shape (text already capped via `capOffText`,
 * image URL already filtered via `gateImageUrl`) — same field set as
 * [de.docgerdsoft.pantrytracker.repository.ScanCandidate.FromOff]. A row
 * only exists if `lookupForPreview` would have returned a non-null
 * `FromOff` candidate on the original fetch.
 *
 * `fetchedAt` is an [Instant] (persisted as epoch-millis INTEGER via
 * [Converters]) so TTL comparisons can use `clock.now() - entry.fetchedAt`
 * directly, matching the `createdAt` / `updatedAt` convention on [Product].
 *
 * Privacy: local-only, never shared via IPC. 30-day TTL bounds growth and
 * limits the staleness window for OFF community edits.
 */
@Entity(tableName = "off_lookup_cache")
data class OffLookupCacheEntry(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String?,
    val imageUrl: String?,
    val resolvingHost: String,
    val fetchedAt: Instant,
)
