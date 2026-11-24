package com.accesolibre.app.domain.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class CompactQrTest {
    // Known-answer vector shared with backend CompactQrCryptoServiceTest, firmware and simulator.
    private val unitSecret = "70ff41ec102789ed76ad4e4613b9169f8ccb6b46d98526f4d3103f16fbf07429"
    private val condoId = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val unitId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val passId = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")
    private val expected = "ASNFZ4mrze8BI0VniavN7w|ERERESIiMzNERFVVVVVVVQ|qqqqqru7zMzd3e7u7u7u7g|" +
        "1795458600|126be26948f327d28a1c2d37"

    @Test
    fun generatesTheSameBytesAsBackendFirmwareAndSimulator() {
        val qr = CompactQr.generate(unitSecret, condoId, unitId, passId, 1795458600L)
        assertEquals(expected, qr)
        assertEquals(104, qr.length)
        assertTrue(qr.length < 120)
        assertEquals(5, qr.split("|").size)
    }

    @Test
    fun base64UrlIdsAreTwentyTwoCharsWithoutPadding() {
        assertEquals("ASNFZ4mrze8BI0VniavN7w", CompactQr.uuidToBase64Url(passId))
        assertEquals(22, CompactQr.uuidToBase64Url(UUID.randomUUID()).length)
    }

    @Test
    fun rejectsInvalidSecret() {
        try {
            CompactQr.generate("zz", condoId, unitId, passId, 1795458600L)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
