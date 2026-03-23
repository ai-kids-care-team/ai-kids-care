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

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody AuthLoginRequest authLoginRequest) {
        TokenResponse response = authService.login(authLoginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public void logout(@RequestBody AuthLogoutRequest authLogoutRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PatchMapping("/password")
    public void changePassword(@RequestBody ChangePasswordRequest changePasswordRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PostMapping("/password-resets")
    public AuthPasswordResetsPost200Response resetPassword(@RequestBody AuthPasswordResetRequest authPasswordResetRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PatchMapping("/password-resets/{resetToken}")
    public void AuthPasswordResetsResetTokenPatch(@PathVariable String resetToken, @RequestBody ResetPasswordRequest resetPasswordRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody AuthRefreshRequest authRefreshRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PostMapping("/register")
    public ResponseEntity<AuthRegisterResponse> register(@RequestBody AuthRegisterRequest authRegisterRequest) {
        AuthRegisterResponse response = authService.register(authRegisterRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verification-codes/{challengeId}/verifications")
    public VerifyVerificationCodeResponse authVerificationCodesChallengeIdVerificationsPost(@PathVariable String challengeId, @RequestBody VerifyVerificationCodeRequest verifyVerificationCodeRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PostMapping("/verification-codes")
    public VerificationCodeCreateResponse verifyCodes(@RequestBody VerificationCodeCreateRequest verificationCodeCreateRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

}
