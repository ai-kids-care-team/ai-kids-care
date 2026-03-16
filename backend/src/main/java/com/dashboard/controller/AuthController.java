package com.dashboard.controller;

import com.dashboard.dto.LoginRequest;
import com.dashboard.dto.LoginResponse;
import com.dashboard.dto.CommonCodeResponse;
import com.dashboard.dto.ForgotPasswordRequest;
import com.dashboard.dto.KindergartenLookupResponse;
import com.dashboard.dto.SignupRequest;
import com.dashboard.dto.SignupResponse;
import com.dashboard.service.AuthService;
import com.dashboard.service.CommonCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/auth"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CommonCodeService commonCodeService;


    @PostMapping({"/login"})
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/register"})    
    public ResponseEntity<SignupResponse> signup(@RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/common-codes"})
    public ResponseEntity<List<CommonCodeResponse>> getCommonCodes(@RequestParam("group") String group) {
        return ResponseEntity.ok(commonCodeService.getActiveCodesByGroup(group));
    }

    @GetMapping({"/kindergartens"})
    public ResponseEntity<List<KindergartenLookupResponse>> searchKindergartens(@RequestParam("keyword") String keyword) {
        return ResponseEntity.ok(authService.searchKindergartens(keyword));
    }

    @PostMapping({"/password/forgot"})
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(Map.of("message", "확인되었습니다."));
    }
}
