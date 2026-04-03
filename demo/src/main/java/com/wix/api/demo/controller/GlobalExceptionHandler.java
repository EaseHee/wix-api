package com.wix.api.demo.controller;

import java.util.Map;

import com.wix.api.http.WixApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WixApiException.class)
    public ResponseEntity<Map<String, Object>> handleWixApiException(WixApiException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                "error", true,
                "statusCode", e.getStatusCode(),
                "message", e.getMessage(),
                "body", e.getErrorBody() != null ? e.getErrorBody() : ""
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", true,
                "statusCode", 400,
                "message", e.getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        String rootCause = e.getMessage();
        Throwable cause = e.getCause();
        while (cause != null) {
            rootCause = cause.getClass().getSimpleName() + ": " + cause.getMessage();
            cause = cause.getCause();
        }
        return ResponseEntity.internalServerError().body(Map.of(
                "error", true,
                "statusCode", 500,
                "message", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                "rootCause", rootCause != null ? rootCause : ""
        ));
    }
}
