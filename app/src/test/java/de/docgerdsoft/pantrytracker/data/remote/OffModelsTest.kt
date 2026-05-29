package de.docgerdsoft.pantrytracker.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test


/**
 * Pure-JVM coverage for the OFF wire/transport DTOs ([OffProduct],
 * [OffApiEnvelope], [OffLookupResult]) — field defaults, the `@SerialName`
 * mapping, and decoding the projected JSON subset the client requests.
 */
class OffModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun offProduct_defaultsAreAllNull() {
        val p = OffProduct()
        assertNull(p.code)
        assertNull(p.productName)
        assertNull(p.brands)
        assertNull(p.imageUrl)
    }

    @Test
    fun offProduct_copyChangesOneFieldAndPreservesTheRest() {
        val base = OffProduct(code = "1", productName = "Coke", brands = "Coca-Cola", imageUrl = "https://x")
        val renamed = base.copy(productName = "Fanta")
        assertEquals("Fanta", renamed.productName)
        // The other three fields must survive the copy untouched.
        assertEquals("1", renamed.code)
        assertEquals("Coca-Cola", renamed.brands)
        assertEquals("https://x", renamed.imageUrl)
    }

    @Test
    fun offProduct_decodesSerialNamedFields() {
        val decoded = json.decodeFromString<OffProduct>(
            """{"code":"5449000000996","product_name":"Coke","brands":"Coca-Cola","image_url":"https://img"}""",
        )
        assertEquals("5449000000996", decoded.code)
        assertEquals("Coke", decoded.productName)
        assertEquals("Coca-Cola", decoded.brands)
        assertEquals("https://img", decoded.imageUrl)
    }

    @Test
    fun offProduct_serialNamesAreRequired_kotlinPropertyKeysDoNotBind() {
        // Pins the @SerialName mapping itself: a payload using the Kotlin
        // property names (camelCase) must NOT bind product_name / image_url.
        // If the @SerialName annotations were dropped, this would start
        // decoding non-null and the test would fail.
        val decoded = json.decodeFromString<OffProduct>(
            """{"productName":"Coke","imageUrl":"https://img"}""",
        )
        assertNull(decoded.productName)
        assertNull(decoded.imageUrl)
    }

    @Test
    fun offProduct_missingFieldsDecodeToNull() {
        val decoded = json.decodeFromString<OffProduct>("""{"code":"111"}""")
        assertEquals("111", decoded.code)
        assertNull(decoded.productName)
        assertNull(decoded.brands)
        assertNull(decoded.imageUrl)
    }

    @Test
    fun offApiEnvelope_statusOneCarriesProduct() {
        val decoded = json.decodeFromString<OffApiEnvelope>(
            """{"status":1,"product":{"product_name":"Coke"}}""",
        )
        assertEquals(1, decoded.status)
        assertEquals("Coke", decoded.product?.productName)
    }

    @Test
    fun offApiEnvelope_statusZeroDefaultsProductToNull() {
        val decoded = json.decodeFromString<OffApiEnvelope>("""{"status":0}""")
        assertEquals(0, decoded.status)
        assertNull(decoded.product)
    }

    @Test
    fun offApiEnvelope_missingStatus_isRejected() {
        // `status` is a non-nullable Int with no default — a response without it
        // must fail decode rather than silently defaulting, so the client treats
        // a malformed envelope as an error instead of a not-found.
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<OffApiEnvelope>("""{"product":{"product_name":"Coke"}}""")
        }
    }

    @Test
    fun offLookupResult_pairsProductWithResolvingHost() {
        val result = OffLookupResult(
            product = OffProduct(productName = "Coke"),
            resolvingHost = OffHost.FOOD,
        )
        assertEquals("Coke", result.product.productName)
        assertEquals(OffHost.FOOD, result.resolvingHost)
        // copy() that changes the host must preserve the product reference.
        val rehosted = result.copy(resolvingHost = OffHost.PET_FOOD)
        assertEquals(OffHost.PET_FOOD, rehosted.resolvingHost)
        assertEquals(result.product, rehosted.product)
    }
}
