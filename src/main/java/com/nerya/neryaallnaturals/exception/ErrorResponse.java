package com.nerya.neryaallnaturals.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        String path,
        Map<String, String> fieldErrors
) {
    public ErrorResponse(String code, String message, String path) {
        this(code, message, Instant.now(), path, null);
    }

    public ErrorResponse(String code, String message, String path, Map<String, String> fieldErrors) {
        this(code, message, Instant.now(), path, fieldErrors);
    }
}
