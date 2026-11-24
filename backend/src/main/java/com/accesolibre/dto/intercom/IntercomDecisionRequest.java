package com.accesolibre.dto.intercom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record IntercomDecisionRequest(
        @NotBlank
        @Pattern(regexp = "^(ANSWER|UNLOCK|REJECT|END)$", message = "action must be ANSWER, UNLOCK, REJECT or END")
        String action
) {
}
