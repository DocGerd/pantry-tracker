package de.docgerdsoft.pantrytracker.data.remote

import kotlinx.serialization.Serializable

/**
 * Wire format for OFF v2 product responses. Matches the minimal subset of the
 * `?fields=code,product_name,brands,image_url,status` projection.
 *
 * The OFF schema is wide and historically inconsistent — we project to the four
 * fields we actually use and accept that any of them may be missing for a given
 * product. `status = 1` means "found", `status = 0` means "not found".
 */
@Serializable
internal data class OffApiEnvelope(
    val status: Int,
    val product: OffProduct? = null,
)

@Serializable
data class OffProduct(
    val code: String? = null,
    @kotlinx.serialization.SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    @kotlinx.serialization.SerialName("image_url") val imageUrl: String? = null,
)
