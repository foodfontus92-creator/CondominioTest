package com.accesolibre.app.domain.qr

import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Compact offline QR protocol (ARCHITECTURE.md section 5). Must stay byte-identical to
 * backend CompactQrCryptoService, firmware qr_verifier.cpp and iot-simulator/simulator.py.
 *
 *   PASS_ID|CONDO_ID|UNIT_ID|EXP_UNIX_TIMESTAMP|HMAC_SHA256_TRUNCATED
 *
 * - ids: UUID 16 bytes (big-endian MSB|LSB) -> Base64URL without padding (22 chars)
 * - mac: first 12 bytes of HMAC-SHA256(key = hex_decode(unit_secret), msg = first four tokens joined by '|')
 *        as lowercase hex (24 chars)
 */
object CompactQr {
    const val MAX_PAYLOAD_LENGTH = 119
    const val HMAC_TRUNCATED_BYTES = 12
    private const val HMAC_ALGORITHM = "HmacSHA256"

    private val b64 = Base64.getUrlEncoder().withoutPadding()

    fun uuidToBase64Url(id: UUID): String {
        val buffer = ByteBuffer.allocate(16).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits)
        return b64.encodeToString(buffer.array())
    }

    fun generate(
        unitSecretHex: String,
        condominiumId: UUID,
        unitId: UUID,
        passId: UUID,
        expiresAtEpochSeconds: Long,
    ): String {
        require(expiresAtEpochSeconds > 0) { "expiration must be a positive Unix timestamp" }
        val signingInput = listOf(
            uuidToBase64Url(passId),
            uuidToBase64Url(condominiumId),
            uuidToBase64Url(unitId),
            expiresAtEpochSeconds.toString(),
        ).joinToString("|")
        val payload = signingInput + "|" + macHex(unitSecretHex, signingInput)
        check(payload.length <= MAX_PAYLOAD_LENGTH) { "QR payload must be shorter than 120 characters" }
        return payload
    }

    fun macHex(unitSecretHex: String, signingInput: String): String {
        val key = hexToBytes(unitSecretHex)
        val mac = Mac.getInstance(HMAC_ALGORITHM).apply { init(SecretKeySpec(key, HMAC_ALGORITHM)) }
        val digest = mac.doFinal(signingInput.toByteArray(Charsets.UTF_8))
        return digest.copyOf(HMAC_TRUNCATED_BYTES).joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length >= 64 && hex.length % 2 == 0) { "unit_secret must be at least 64 hex chars" }
        return ByteArray(hex.length / 2) { i ->
            val hi = Character.digit(hex[2 * i], 16)
            val lo = Character.digit(hex[2 * i + 1], 16)
            require(hi >= 0 && lo >= 0) { "unit_secret is not valid hex" }
            ((hi shl 4) or lo).toByte()
        }
    }
}
