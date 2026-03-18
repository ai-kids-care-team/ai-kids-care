package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.api.AuthApi;
import com.ai_kids_care.v1.dto.*;
import com.ai_kids_care.v1.service.AuthService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.annotation.Generated;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.20.0")
@RestController
@RequiredArgsConstructor
public class AuthApiController implements AuthApi {

    private final AuthService authService;

    /**
     * POST /v1/auth/login : Login
     *
     * @param authLoginRequest (required)
     * @return OK (status code 200)
     * or Invalid credentials or account not ACTIVE (status code 401)
     * @see AuthApi#AuthLogin
     */
    @Override
    public ResponseEntity<TokenResponse> AuthLogin(@Parameter(name = "AuthLoginRequest", description = "", required = true) @RequestBody AuthLoginRequest authLoginRequest) {
        TokenResponse response = authService.login(authLoginRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /v1/auth/logout : Logout (revoke refresh token)
     *
     * @param authLogoutRequest (required)
     * @return No Content (status code 204)
     * @see AuthApi#AuthLogoutPost
     */
    @Override
    public void AuthLogoutPost(@Parameter(name = "AuthLogoutRequest", description = "", required = true) @RequestBody AuthLogoutRequest authLogoutRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    /**
     * PATCH /v1/auth/password : Change password (current password required)
     *
     * @param changePasswordRequest (required)
     * @return No Content (status code 204)
     * or Validation error (weak password, missing fields) (status code 400)
     * or Unauthorized (not logged in / invalid token) (status code 401)
     * or Current password mismatch (status code 403)
     * @see AuthApi#AuthPasswordPatch
     */
    @Override
    public void AuthPasswordPatch(@Parameter(name = "ChangePasswordRequest", description = "", required = true) @RequestBody ChangePasswordRequest changePasswordRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    /**
     * POST /v1/auth/password-resets : Start forgot password flow
     * Always returns 200 to prevent account enumeration.
     *
     * @param authPasswordResetRequest (required)
     * @return OK (status code 200)
     * @see AuthApi#v1AuthPasswordResetsPost
     */
    @Override
    public AuthPasswordResetsPost200Response v1AuthPasswordResetsPost(@Parameter(name = "AuthPasswordResetRequest", description = "", required = true) @RequestBody AuthPasswordResetRequest authPasswordResetRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    /**
     * PATCH /v1/auth/password-resets/{resetToken} : Reset password with reset token
     *
     * @param resetToken           Password reset token from email/SMS link (required)
     * @param resetPasswordRequest (required)
     * @return No Content (status code 204)
     * or Invalid token / weak password (status code 400)
     * @see AuthApi#AuthPasswordResetsResetTokenPatch
     */
    @Override
    public void AuthPasswordResetsResetTokenPatch(@Parameter(name = "resetToken", description = "Password reset token from email/SMS link", required = true, in = ParameterIn.PATH) @PathVariable("resetToken") String resetToken, @Parameter(name = "ResetPasswordRequest", description = "", required = true) @RequestBody ResetPasswordRequest resetPasswordRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    /**
     * POST /v1/auth/refresh : Refresh access token
     *
     * @param authRefreshRequest (required)
     * @return OK (status code 200)
     * or Invalid refresh token (status code 401)
     * @see AuthApi#AuthRefreshPost
     */
    @Override
    public TokenResponse AuthRefreshPost(@Parameter(name = "AuthRefreshRequest", description = "", required = true) @RequestBody AuthRefreshRequest authRefreshRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    /**
     * POST /v1/auth/register : Register user account
     *
     * @param authRegisterRequest (required)
     * @return Created (status code 201)
     * or Validation error (status code 400)
     * or Unique conflict (email/phone/loginId) (status code 409)
     * @see AuthApi#AuthRegisterPost
     */
    @Override
    public AuthRegisterResponse AuthRegisterPost(@Parameter(name = "AuthRegisterRequest", description = "", required = true) @RequestBody AuthRegisterRequest authRegisterRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    /**
     * POST /v1/auth/verification-codes/{challengeId}/verifications : Verify OTP code
     *
     * @param challengeId                   Verification challenge identifier returned by create endpoint (required)
     * @param verifyVerificationCodeRequest (required)
     * @return Verified. Returns a short-lived token for the next step. (status code 200)
     * or Invalid or expired code (or validation error) (status code 400)
     * or Too many attempts / rate limited (status code 429)
     * @see AuthApi#v1AuthVerificationCodesChallengeIdVerificationsPost
     */
    @Override
    public VerifyVerificationCodeResponse v1AuthVerificationCodesChallengeIdVerificationsPost(@Parameter(name = "challengeId", description = "Verification challenge identifier returned by create endpoint", required = true, in = ParameterIn.PATH) @PathVariable("challengeId") String challengeId, @Parameter(name = "VerifyVerificationCodeRequest", description = "", required = true) @RequestBody VerifyVerificationCodeRequest verifyVerificationCodeRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

    /**
     * POST /v1/auth/verification-codes : Send OTP verification code
     *
     * @param verificationCodeCreateRequest (required)
     * @return Created (status code 201)
     * or Rate limited (status code 429)
     * @see AuthApi#v1AuthVerificationCodesPost
     */
    @Override
    public VerificationCodeCreateResponse v1AuthVerificationCodesPost(@Parameter(name = "VerificationCodeCreateRequest", description = "", required = true) @RequestBody VerificationCodeCreateRequest verificationCodeCreateRequest) {
        throw new IllegalArgumentException("Not implemented");
    }

}
