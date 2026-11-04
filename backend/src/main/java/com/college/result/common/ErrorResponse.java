package com.college.result.common;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(boolean success, int status, String code, String message,
                            Map<String, String> fieldErrors, Instant timestamp) {

    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(false, status, code, message, Map.of(), Instant.now());
    }

    public static ErrorResponse of(int status, String code, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(false, status, code, message, fieldErrors, Instant.now());
    }
}
