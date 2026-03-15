package com.dashboard.exception;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Pattern DUPLICATE_KEY_COLUMN_PATTERN =
            Pattern.compile("Key \\(([^)]+)\\)=");

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String raw = extractMessage(ex);
        String mappedMessage = mapDuplicateMessage(raw);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", mappedMessage));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    private String extractMessage(Exception ex) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        if (root != null && root.getMessage() != null && !root.getMessage().isBlank()) {
            return root.getMessage();
        }
        return ex.getMessage() == null ? "" : ex.getMessage();
    }

    private String mapDuplicateMessage(String raw) {
        String message = raw == null ? "" : raw;

        // 제약명 기반 우선 매핑
        if (message.contains("uq_user_account_phone")) {
            return "전화번호는 중복입니다.";
        }
        if (message.contains("uq_user_account_login_id")) {
            return "로그인 ID는 중복입니다.";
        }
        if (message.contains("uq_user_account_email")) {
            return "이메일은 중복입니다.";
        }

        // PostgreSQL duplicate key 메시지 일반화: Key (column)=...
        if (message.contains("duplicate key value violates unique constraint")) {
            Matcher matcher = DUPLICATE_KEY_COLUMN_PATTERN.matcher(message);
            if (matcher.find()) {
                String column = matcher.group(1);
                return switch (column) {
                    case "phone" -> "전화번호는 중복입니다.";
                    case "login_id" -> "로그인 ID는 중복입니다.";
                    case "email" -> "이메일은 중복입니다.";
                    default -> String.format("%s 값은 중복입니다.", column);
                };
            }
            return "중복된 값이 존재합니다.";
        }

        return message.isBlank() ? "요청 처리 중 오류가 발생했습니다." : message;
    }
}
