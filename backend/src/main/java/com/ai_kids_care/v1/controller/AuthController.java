package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.*;
import com.ai_kids_care.v1.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping
    public ResponseEntity<TokenResponse> login(@RequestBody AuthLoginRequest authLoginRequest) {
        TokenResponse response = authService.login(authLoginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public void logout(@RequestBody AuthLogoutRequest authLogoutRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PatchMapping
    public void changePassword(@RequestBody ChangePasswordRequest changePasswordRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PostMapping
    public AuthPasswordResetsPost200Response resetPassword(@RequestBody AuthPasswordResetRequest authPasswordResetRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PatchMapping
    public void AuthPasswordResetsResetTokenPatch(@PathVariable String resetToken, @RequestBody ResetPasswordRequest resetPasswordRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PostMapping
    public TokenResponse refresh(@RequestBody AuthRefreshRequest authRefreshRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PostMapping
    public ResponseEntity<AuthRegisterResponse> register(@RequestBody AuthRegisterRequest authRegisterRequest) {
        AuthRegisterResponse response = authService.register(authRegisterRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public VerifyVerificationCodeResponse authVerificationCodesChallengeIdVerificationsPost(@PathVariable String challengeId, @RequestBody VerifyVerificationCodeRequest verifyVerificationCodeRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PostMapping
    public VerificationCodeCreateResponse verifyCodes(@RequestBody VerificationCodeCreateRequest verificationCodeCreateRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

}
