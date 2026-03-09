package com.dashboard.controller;

import com.dashboard.dto.LoginRequest;
import com.dashboard.dto.LoginResponse;
import com.dashboard.dto.ChildLookupResponse;
import com.dashboard.dto.CommonCodeResponse;
import com.dashboard.dto.SignupRequest;
import com.dashboard.dto.SignupResponse;
import com.dashboard.service.AuthService;
import com.dashboard.service.CommonCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CommonCodeService commonCodeService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/children")
    public ResponseEntity<List<ChildLookupResponse>> searchChildren(@RequestParam("name") String name) {
        return ResponseEntity.ok(authService.searchChildrenByName(name));
    }

    @GetMapping("/common-codes")
    public ResponseEntity<List<CommonCodeResponse>> getCommonCodes(@RequestParam("group") String group) {
        return ResponseEntity.ok(commonCodeService.getActiveCodesByGroup(group));
    }
}
