package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.*;
import com.ai_kids_care.v1.security.AuthenticatedSession;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContext;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextService;
import com.ai_kids_care.v1.service.AuthService;
import com.ai_kids_care.v1.vo.*;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.List;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EffectiveAuthorizationContextService authorizationContextService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @PostMapping("/login")
    public ResponseEntity<AuthSessionVO> login(
            @RequestBody AuthLoginDTO authLoginDTO,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthenticatedSession authenticatedSession = authService.login(authLoginDTO);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                authenticatedSession.principal(),
                null,
                List.of(new SimpleGrantedAuthority("SESSION_AUTHENTICATED"))
        );
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);
        return ResponseEntity.ok(authenticatedSession.profile());
    }

    @GetMapping("/session")
    public ResponseEntity<AuthSessionVO> session(
            Authentication authentication
    ) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication failed");
        }
        return ResponseEntity.ok(
                EffectiveAuthorizationContextHolder.require().toSessionProfile());
    }

    @PostMapping("/session/tenant-context")
    public ResponseEntity<TenantContextVO> selectTenantContext(
            @Valid @RequestBody TenantContextRequest tenantContextRequest,
            HttpServletRequest request
    ) {
        EffectiveAuthorizationContext context =
                EffectiveAuthorizationContextHolder.require();
        return ResponseEntity.ok(authorizationContextService.selectTenant(
                context,
                tenantContextRequest.kindergartenId(),
                request.getSession()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenVO> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(new CsrfTokenVO(
                csrfToken.getToken(),
                csrfToken.getHeaderName(),
                csrfToken.getParameterName()
        ));
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
