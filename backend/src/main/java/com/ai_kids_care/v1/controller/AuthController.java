package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.*;
import com.ai_kids_care.v1.service.AuthService;
import com.ai_kids_care.v1.service.KindergartenService;
import com.ai_kids_care.v1.vo.*;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final KindergartenService kindergartenService;

    @PostMapping("/login")
    public ResponseEntity<TokenVO> login(@RequestBody AuthLoginDTO authLoginDTO) {
        TokenVO response = authService.login(authLoginDTO);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public void logout(@RequestBody AuthLogoutDTO authLogoutDTO) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PatchMapping("/password")
    public void changePassword(@RequestBody ChangePasswordRequest changePasswordRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PostMapping("/password-resets")
    public ResponseEntity<AuthPasswordResetsVO> resetPassword(@RequestBody AuthPasswordResetDTO authPasswordResetDTO) {
        authService.passwordResets(authPasswordResetDTO);
        throw new IllegalArgumentException("Not implemented");
    }

    @PatchMapping("/password-resets/{resetToken}")
    public void AuthPasswordResetsResetTokenPatch(@PathVariable String resetToken, @RequestBody ResetPasswordRequest resetPasswordRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenVO> refresh(@RequestBody AuthRefreshRequest authRefreshRequest) {
        TokenVO response = authService.refresh(authRefreshRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthRegisterResponse> register(@Parameter(name = "AuthRegisterRequest", required = true) @RequestBody AuthRegisterDTO authRegisterDTO) {
        AuthRegisterResponse response = authService.register(authRegisterDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verification-codes/{challengeId}/verifications")
    public ResponseEntity<VerifyVerificationCodeVO> authVerificationCodesChallengeIdVerificationsPost(@PathVariable String challengeId, @RequestBody VerifyVerificationCodeRequest verifyVerificationCodeRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    @PostMapping("/verification-codes")
    public ResponseEntity<VerificationCodeCreateVO> verifyCodes(@RequestBody VerificationCodeCreateRequest verificationCodeCreateRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    /**
     * /register/availability?field=loginId|email|phone&amp;value=...
     */
    @GetMapping("/register/availability")
    public ResponseEntity<AuthRegisterVO> registerFieldAvailability(@RequestParam(value = "field") String field,
                                                                    @RequestParam(value = "value") String value) {
        return ResponseEntity.ok(authService.checkRegisterFieldAvailability(field, value));
    }
}
