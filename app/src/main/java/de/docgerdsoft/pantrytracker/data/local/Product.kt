package de.docgerdsoft.pantrytracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

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
    val createdAt: Instant,
    val updatedAt: Instant,
)
