package com.accesolibre.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class AccessDeniedDomainException extends ApiException {

    public AccessDeniedDomainException(String code, String message, Map<String, Object> details) {
        super(HttpStatus.FORBIDDEN, code, message, details);
    }

    public AccessDeniedDomainException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
