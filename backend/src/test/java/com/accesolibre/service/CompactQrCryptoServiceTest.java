package com.accesolibre.service;

import com.accesolibre.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompactQrCryptoServiceTest {

    private CompactQrCryptoService cryptoService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        cryptoService = new CompactQrCryptoService(properties);
    }

    @Test
    void shouldGenerateAndVerifyCompactOfflineQrStrictlyUnder120Chars() {
        UUID passId = UUID.randomUUID();
        UUID condoId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3600);

        String condoSecret = cryptoService.generateRandomHmacSecretHex();
        String unitSecret = cryptoService.deriveUnitSecretHex(condoSecret, unitId);

        String qrPayload = cryptoService.generateQrPayload(passId, condoId, unitId, expiresAt, unitSecret);

        assertThat(qrPayload.length()).isEqualTo(104);
        assertThat(qrPayload.length()).isLessThan(120);
        assertThat(qrPayload.split("\\|", -1)).hasSize(5);

        Optional<CompactQrCryptoService.ParsedQrPayload> parsedOpt = cryptoService.parseQrPayload(qrPayload);
        assertThat(parsedOpt).isPresent();
        CompactQrCryptoService.ParsedQrPayload parsed = parsedOpt.get();

        assertThat(parsed.passId()).isEqualTo(passId);
        assertThat(parsed.condoId()).isEqualTo(condoId);
        assertThat(parsed.unitId()).isEqualTo(unitId);
        assertThat(parsed.expUnix()).isEqualTo(expiresAt.getEpochSecond());

        assertThat(cryptoService.verifyTruncatedHmac(unitSecret, parsed.signingInput(), parsed.truncatedMacHex()))
                .isTrue();

        // Cross-unit secret MUST fail verification (unit isolation)
        String otherUnitSecret = cryptoService.deriveUnitSecretHex(condoSecret, UUID.randomUUID());
        assertThat(cryptoService.verifyTruncatedHmac(otherUnitSecret, parsed.signingInput(), parsed.truncatedMacHex()))
                .isFalse();
    }

    @Test
    void shouldMatchKnownAnswerVectorSharedWithFirmwareAndSimulator() {
        // Same constants as iot-simulator/test_simulator.py (firmware implements the identical algorithm)
        String condoSecret = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
        UUID condoId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID unitId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID passId = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

        String unitSecret = cryptoService.deriveUnitSecretHex(condoSecret, unitId);
        assertThat(unitSecret).isEqualTo("70ff41ec102789ed76ad4e4613b9169f8ccb6b46d98526f4d3103f16fbf07429");

        String qr = cryptoService.generateQrPayload(passId, condoId, unitId, Instant.ofEpochSecond(1795458600L), unitSecret);
        assertThat(qr).isEqualTo("ASNFZ4mrze8BI0VniavN7w|ERERESIiMzNERFVVVVVVVQ|qqqqqru7zMzd3e7u7u7u7g|"
                + "1795458600|126be26948f327d28a1c2d37");
    }

    @Test
    void shouldRejectJwtFormatAsQrPayload() {
        String fakeJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        assertThat(cryptoService.parseQrPayload(fakeJwt)).isEmpty();
    }
}
