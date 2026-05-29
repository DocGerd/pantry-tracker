package de.docgerdsoft.pantrytracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"], unique = true)],
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,
    val name: String,
    val brand: String? = null,
    val imageUrl: String? = null,
    val quantity: Int,
    // #191: opt-in restock tracking. lowLimit null ⇒ never tracked, never on the
    // buying list. defaultBuyAmount is how much one "Bought" tap adds; defaults to
    // 1 so the migration is safe for existing rows.
    val lowLimit: Int? = null,
    val defaultBuyAmount: Int = 1,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * #191 buying-list membership. Mirrors the SQL in [ProductDao.observeBuyingList]
 * exactly (`lowLimit != null && quantity <= lowLimit`). A null `lowLimit` means
 * the item is opt-out and never appears.
 */
val Product.isOnBuyingList: Boolean
    get() = lowLimit?.let { quantity <= it } ?: false
