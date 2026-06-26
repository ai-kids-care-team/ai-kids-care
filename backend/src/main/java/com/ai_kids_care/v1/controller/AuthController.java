package com.ai_kids_care.v1.controller;

import com.ai_kids_care.v1.dto.*;
import com.ai_kids_care.v1.security.AuthenticatedSession;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContext;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextHolder;
import com.ai_kids_care.v1.security.EffectiveAuthorizationContextService;
import com.ai_kids_care.v1.security.SessionRevocationService;
import com.ai_kids_care.v1.security.audit.AuditAction;
import com.ai_kids_care.v1.security.audit.AuditEvent;
import com.ai_kids_care.v1.security.audit.AuditResult;
import com.ai_kids_care.v1.security.audit.SecurityAuditWriter;
import com.ai_kids_care.v1.service.AuthService;
import com.ai_kids_care.v1.type.UserRoleAssignmentScopeType;
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
    private final SessionRevocationService sessionRevocationService;
    private final SecurityAuditWriter auditWriter;

    @PostMapping("/login")
    public ResponseEntity<AuthSessionVO> login(
            @Valid @RequestBody AuthLoginDTO authLoginDTO,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthenticatedSession authenticatedSession;
        try {
            authenticatedSession = authService.login(authLoginDTO);
        } catch (ResponseStatusException e) {
            // 限流/临时锁定(429)는 LOGIN_THROTTLE 으로 service 에서 이미 감사함 — 여기서 LOGIN_FAILURE
            // 로 이중 기록하지 않는다. 그 외(401 등) 는 LOGIN_FAILURE 로 기록한다.
            // actor 未知（평台级事件, kindergarten_id NULL）; identifier 不记录——S1 email/phone 가능(SPEC §282)。
            if (e.getStatusCode().value() != HttpStatus.TOO_MANY_REQUESTS.value()) {
                auditWriter.record(AuditEvent.builder()
                        .action(AuditAction.LOGIN_FAILURE)
                        .result(AuditResult.FAILURE)
                        .scopeType(UserRoleAssignmentScopeType.PLATFORM)
                        .build());
            }
            throw e;
        }
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

        AuthSessionVO profile = authenticatedSession.profile();
        auditWriter.record(AuditEvent.builder()
                .action(AuditAction.LOGIN_SUCCESS)
                .result(AuditResult.SUCCESS)
                .actorUserId(profile.userId())
                .scopeType(UserRoleAssignmentScopeType.valueOf(profile.scopeType()))
                .kindergartenId(
                        UserRoleAssignmentScopeType.KINDERGARTEN.name().equals(profile.scopeType())
                                ? profile.scopeId()
                                : null)
                .effectiveRole(profile.effectiveRole())
                .build());
        return ResponseEntity.ok(profile);
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
        try {
            TenantContextVO result = authorizationContextService.selectTenant(
                    context,
                    tenantContextRequest.kindergartenId(),
                    request.getSession()
            );
            // 平台级事件：scope=PLATFORM，kindergarten_id NULL（不伪造），所选 tenant 落 resource_id（SPEC §284）。
            auditWriter.record(AuditEvent.builder()
                    .action(AuditAction.TENANT_CONTEXT_SELECT)
                    .result(AuditResult.SUCCESS)
                    .actorUserId(context.userId())
                    .scopeType(UserRoleAssignmentScopeType.PLATFORM)
                    .effectiveRole(context.role().name())
                    .resourceType("KINDERGARTEN")
                    .resourceId(result.selectedKindergartenId())
                    .build());
            return ResponseEntity.ok(result);
        } catch (ResponseStatusException e) {
            // 非平台账号尝试切换 tenant（403）等：审计拒绝后重抛。
            auditWriter.record(AuditEvent.builder()
                    .action(AuditAction.TENANT_CONTEXT_SELECT)
                    .result(AuditResult.DENIED)
                    .actorUserId(context.userId())
                    .scopeType(context.scopeType())
                    .kindergartenId(
                            context.scopeType() == UserRoleAssignmentScopeType.KINDERGARTEN
                                    ? context.activeKindergartenId()
                                    : null)
                    .effectiveRole(context.role().name())
                    .build());
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        // 失效前审计（context 仍有效）
        EffectiveAuthorizationContextHolder.get().ifPresent(context ->
                auditWriter.record(sessionEvent(context, AuditAction.LOGOUT)));
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll() {
        EffectiveAuthorizationContext context =
                EffectiveAuthorizationContextHolder.require();
        sessionRevocationService.revokeAllForUser(context.userId());
        auditWriter.record(sessionEvent(context, AuditAction.SESSION_REVOKE_ALL));
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /** 会话事件审计（登出 / 全量吊销）：actor=本人，scope/role 取自当前 context。 */
    private AuditEvent sessionEvent(EffectiveAuthorizationContext context, AuditAction action) {
        return AuditEvent.builder()
                .action(action)
                .result(AuditResult.SUCCESS)
                .actorUserId(context.userId())
                .scopeType(context.scopeType())
                .kindergartenId(
                        context.scopeType() == UserRoleAssignmentScopeType.KINDERGARTEN
                                ? context.activeKindergartenId()
                                : null)
                .effectiveRole(context.role().name())
                .build();
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
