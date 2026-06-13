package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.*;
import com.ai_kids_care.v1.service.AuthService;
import com.ai_kids_care.v1.vo.*;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<TokenVO> login(@RequestBody AuthLoginDTO authLoginDTO) {
        TokenVO response = authService.login(authLoginDTO);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenVO> refresh(@RequestBody AuthRefreshRequest authRefreshRequest) {
        TokenVO response = authService.refresh(authRefreshRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthRegisterResponse> register(@Parameter(name = "AuthRegisterRequest", required = true) @Valid @RequestBody AuthRegisterDTO authRegisterDTO) {
        AuthRegisterResponse response = authService.register(authRegisterDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/guardian-child-verifications")
    public ResponseEntity<GuardianChildVerificationResponse> verifyGuardianChild(
            @Valid @RequestBody GuardianChildVerificationRequest request
    ) {
        return ResponseEntity.ok(authService.verifyGuardianChild(request));
    }

    /**
     * /register/availability?field=loginId|email|phone&amp;value=...
     */
    @GetMapping("/register/availability")
    public ResponseEntity<AuthRegisterVO> registerFieldAvailability(@RequestParam(value = "field") String field,
                                                                    @RequestParam(value = "value") String value) {
        return ResponseEntity.ok(authService.checkRegisterFieldAvailability(field, value));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleAuthResponseStatusException(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Request rejected" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("error", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleAuthValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Invalid registration request" : error.getDefaultMessage())
                .orElse("Invalid registration request");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
