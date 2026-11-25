package com.accesolibre.dto.qr;

import java.util.UUID;

public record UnitSecretResponse(
        UUID condominiumId,
        String condoIdB64,
        UUID unitId,
        String unitIdB64,
        String unitNumber,
        String blockName,
        String unitSecret,
        Integer hmacKeyVersion,
        String qrFormat,
        Integer hmacTruncatedHexLength
) {
}
