package com.accesolibre.service;

import com.accesolibre.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Cryptographic engine for AccesoLibre's compact offline QR protocol.
 *
 * Canonical format (104 ASCII chars, strictly < 120 chars, NO JWT):
 *   PASS_ID|CONDO_ID|UNIT_ID|EXP_UNIX_TIMESTAMP|HMAC_SHA256_TRUNCATED
 *
 * Key hierarchy:
 *   unit_secret = HMAC-SHA256(key = hex_decode(condo_hmac_secret), msg = UTF8(UNIT_ID_B64))
 *   signature   = HMAC-SHA256(key = hex_decode(unit_secret), msg = UTF8(signing_input))[0..11] (24 hex chars, 96 bits)
 */
@Service
public class CompactQrCryptoService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final AppProperties properties;

    public CompactQrCryptoService(AppProperties properties) {
        this.properties = properties;
    }

    public String generateRandomHmacSecretHex() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HEX_FORMAT.formatHex(bytes);
    }

    public String encodeUuidBase64Url(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return BASE64_URL_ENCODER.encodeToString(buffer.array());
    }

    public Optional<UUID> decodeUuidBase64Url(String base64Url) {
        if (base64Url == null || base64Url.length() != 22) {
            return Optional.empty();
        }
        try {
            byte[] bytes = BASE64_URL_DECODER.decode(base64Url);
            if (bytes.length != 16) {
                return Optional.empty();
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            long mostSig = buffer.getLong();
            long leastSig = buffer.getLong();
            return Optional.of(new UUID(mostSig, leastSig));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Derives the deterministic per-unit secret from the condominium master HMAC secret.
     * ESP32 reproduces this in a single mbedtls_md_hmac call without storing a unit table.
     */
    public String deriveUnitSecretHex(String condoHmacSecretHex, UUID unitId) {
        String unitIdB64 = encodeUuidBase64Url(unitId);
        byte[] masterKey = decodeHexKey(condoHmacSecretHex);
        byte[] derived = hmacSha256(masterKey, unitIdB64.getBytes(StandardCharsets.UTF_8));
        return HEX_FORMAT.formatHex(derived);
    }

    /**
     * Generates a compact pipe-separated QR payload strictly shorter than 120 characters.
     */
    public String generateQrPayload(UUID passId, UUID condoId, UUID unitId, Instant expiresAt, String unitSecretHex) {
        String passIdB64 = encodeUuidBase64Url(passId);
        String condoIdB64 = encodeUuidBase64Url(condoId);
        String unitIdB64 = encodeUuidBase64Url(unitId);
        long expUnix = expiresAt.getEpochSecond();

        String signingInput = passIdB64 + "|" + condoIdB64 + "|" + unitIdB64 + "|" + expUnix;
        String truncatedMac = computeTruncatedHmacHex(unitSecretHex, signingInput);
        String payload = signingInput + "|" + truncatedMac;

        if (payload.length() > properties.getQr().getMaxPayloadLength()) {
            throw new IllegalStateException("Compact QR payload exceeded maximum allowed length (< 120 chars)");
        }
        return payload;
    }

    public String computeTruncatedHmacHex(String unitSecretHex, String signingInput) {
        byte[] keyBytes = decodeHexKey(unitSecretHex);
        byte[] fullDigest = hmacSha256(keyBytes, signingInput.getBytes(StandardCharsets.UTF_8));
        int truncatedLen = properties.getQr().getHmacTruncatedBytes();
        byte[] truncated = new byte[truncatedLen];
        System.arraycopy(fullDigest, 0, truncated, 0, truncatedLen);
        return HEX_FORMAT.formatHex(truncated);
    }

    public boolean verifyTruncatedHmac(String unitSecretHex, String signingInput, String providedMacHex) {
        if (providedMacHex == null) {
            return false;
        }
        int expectedHexLen = properties.getQr().getHmacTruncatedBytes() * 2;
        if (providedMacHex.length() != expectedHexLen) {
            return false;
        }
        String expectedMac = computeTruncatedHmacHex(unitSecretHex, signingInput);
        return MessageDigest.isEqual(
                expectedMac.getBytes(StandardCharsets.UTF_8),
                providedMacHex.toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }

    public Optional<ParsedQrPayload> parseQrPayload(String qrPayload) {
        if (qrPayload == null || qrPayload.isBlank() || qrPayload.length() > properties.getQr().getMaxPayloadLength()) {
            return Optional.empty();
        }
        // Explicitly reject JWT-like tokens (3 dot-separated Base64 segments)
        if (!qrPayload.contains("|")) {
            return Optional.empty();
        }
        String[] parts = qrPayload.split("\\|", -1);
        if (parts.length != 5) {
            return Optional.empty();
        }
        Optional<UUID> passIdOpt = decodeUuidBase64Url(parts[0]);
        Optional<UUID> condoIdOpt = decodeUuidBase64Url(parts[1]);
        Optional<UUID> unitIdOpt = decodeUuidBase64Url(parts[2]);
        if (passIdOpt.isEmpty() || condoIdOpt.isEmpty() || unitIdOpt.isEmpty()) {
            return Optional.empty();
        }

        long expUnix;
        try {
            expUnix = Long.parseLong(parts[3]);
            if (expUnix <= 0) {
                return Optional.empty();
            }
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }

        int expectedMacLen = properties.getQr().getHmacTruncatedBytes() * 2;
        String macHex = parts[4].toLowerCase();
        if (macHex.length() != expectedMacLen || !macHex.matches("^[0-9a-f]+$")) {
            return Optional.empty();
        }

        String signingInput = parts[0] + "|" + parts[1] + "|" + parts[2] + "|" + parts[3];
        return Optional.of(new ParsedQrPayload(
                passIdOpt.get(),
                condoIdOpt.get(),
                unitIdOpt.get(),
                parts[0],
                parts[1],
                parts[2],
                expUnix,
                Instant.ofEpochSecond(expUnix),
                macHex,
                signingInput
        ));
    }

    public int getExpectedHmacHexLength() {
        return properties.getQr().getHmacTruncatedBytes() * 2;
    }

    public long getClockSkewSeconds() {
        return properties.getQr().getClockSkewSeconds();
    }

    private byte[] decodeHexKey(String hexKey) {
        if (hexKey == null || hexKey.length() < 64 || (hexKey.length() % 2 != 0)) {
            throw new IllegalArgumentException("HMAC secret must be a valid hex string of at least 32 bytes (64 hex chars)");
        }
        return HEX_FORMAT.parseHex(hexKey);
    }

    private byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", ex);
        }
    }

    public record ParsedQrPayload(
            UUID passId,
            UUID condoId,
            UUID unitId,
            String passIdB64,
            String condoIdB64,
            String unitIdB64,
            long expUnix,
            Instant expiresAt,
            String truncatedMacHex,
            String signingInput
    ) {
    }
}
