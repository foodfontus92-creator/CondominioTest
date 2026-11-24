package com.accesolibre.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenTenantException extends ApiException {

    public ForbiddenTenantException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN_TENANT_ACCESS", message);
    }
}
