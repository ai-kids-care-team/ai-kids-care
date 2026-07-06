package com.ai_kids_care.v1.controller;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(
            EntityNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Resource not found"));
    }

    // Global fallback for services that throw ResponseStatusException directly (e.g.
    // NotificationPreferenceService's 400 on a one-sided quiet-hours window); mirrors
    // AuthController's controller-local handler of the same shape for controllers with no such
    // local override — never leaks the exception message verbatim beyond the intended `reason`.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(
            ResponseStatusException exception
    ) {
        String message = exception.getReason() == null ? "Request rejected" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("error", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, List<Map<String, String>>>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "message", fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value"
                ))
                .collect(Collectors.toList());
        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request"));
    }
}
