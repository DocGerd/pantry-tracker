package de.docgerdsoft.pantrytracker.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun offProduct_copyAndEquality() {
        val base = OffProduct(code = "1", productName = "Coke", brands = "Coca-Cola", imageUrl = "https://x")
        assertEquals(base, base.copy())
        assertEquals("Fanta", base.copy(productName = "Fanta").productName)
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
    fun offLookupResult_pairsProductWithResolvingHost() {
        val result = OffLookupResult(
            product = OffProduct(productName = "Coke"),
            resolvingHost = "https://world.openfoodfacts.org/",
        )
        assertEquals("Coke", result.product.productName)
        assertEquals("https://world.openfoodfacts.org/", result.resolvingHost)
        assertEquals(result, result.copy())
    }
}
