package com.accesolibre.dto.unit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchCreateUnitsRequest(
        @NotBlank(message = "El nombre del Block es obligatorio")
        @Size(max = 64)
        String blockName,

        @NotEmpty(message = "Debes ingresar al menos un número de vivienda")
        @Size(max = 200, message = "Máximo 200 viviendas por lote")
        List<@NotBlank @Size(max = 32) String> unitNumbers
) {
}
