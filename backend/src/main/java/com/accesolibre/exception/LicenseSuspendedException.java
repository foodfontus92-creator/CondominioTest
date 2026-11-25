package com.accesolibre.exception;

import org.springframework.http.HttpStatus;

public class LicenseSuspendedException extends ApiException {

    public static final String CODE = "LICENSE_SUSPENDED_OR_EXPIRED";
    public static final String DEFAULT_MESSAGE =
            "Licencia del recinto vencida o suspendida. Contacte a la administración.";

    public LicenseSuspendedException() {
        super(HttpStatus.PAYMENT_REQUIRED, CODE, DEFAULT_MESSAGE);
    }

    public LicenseSuspendedException(String message) {
        super(HttpStatus.PAYMENT_REQUIRED, CODE, message);
    }
}
