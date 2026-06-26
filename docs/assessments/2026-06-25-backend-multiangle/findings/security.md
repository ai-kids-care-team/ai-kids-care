# Security Findings — backend (Spring Boot / Java 21)

Angle: security · Component: backend · Reviewer: security-analyst
Scope: `C:\ai-kids-care\backend` @ develop. Method: static read + Explore breadth map. No dynamic run (no Java/containers booted) → high-confidence items are precisely located; dynamic-only claims are flagged.

## Overall posture (context for the findings below)
The auth/tenant model is, on the whole, strong and intentional:
- **Per-request re-resolution** (`EffectiveAuthorizationContextFilter` → `EffectiveAuthorizationContextService.resolve`) re-checks user `status==ACTIVE`, role-assignment id, and membership id on **every** request → role revoke / user disable / membership end take effect on the next request without TTL wait, and session is invalidated (401).
- **SQL-level tenant filtering**: cross-tenant-sensitive repositories expose `findByIdAndKindergarten_Id`-style methods; services pull tenant from `EffectiveAuthorizationContextHolder.requireActiveKindergartenId()` (never from client body) and uniformly throw `EntityNotFoundException` → **hidden 404, not 403** (no existence oracle).
- **RRN**: HMAC-SHA-256 + env pepper, no reversible storage, fail-fast `@NotBlank` on the pepper.
- **Secrets**: all env-driven, fail-fast (`RrnHashConfig`, `InternalAiServiceConfig`, `CameraStreamCryptoConfig`, Pushover/Solapi); `.env.example` files carry placeholders only — no hardcoded production secret.
- **Injection**: all `JdbcTemplate`/JPQL use bound parameters; no string concatenation into queries; no Cypher built from user input (graph path is `denyAll()`).
- **Default-deny**: `AuthorizationPolicy.isAllowed` returns false with no context; `GraphService` / `EventReviewService.getLatestReview` carry `@PreAuthorize("denyAll()")` placeholders.

The findings below are the residual gaps, ordered by severity.

---

```yaml
- id: SEC-01
  angle: security
  component: backend
  severity: medium
  title: Tenant CRUD mutations (camera streams, rooms, classes, event reviews) are not written to audit_logs as SUCCESS
  location: backend/src/main/java/com/ai_kids_care/v1/security/audit/AuditAction.java:9-43 (enum has no CREATE/UPDATE/DELETE actions for tenant resources); backend/src/main/java/com/ai_kids_care/v1/service/CameraStreamService.java:71,99 ; backend/src/main/java/com/ai_kids_care/v1/service/EventReviewService.java:46
  evidence: |
    AuditAction enum only covers: LOGIN_SUCCESS/FAILURE/THROTTLE, LOGOUT, SESSION_REVOKE_ALL,
    TENANT_CONTEXT_SELECT, KINDERGARTEN_/PLATFORM_ approve/reject/disable, AUTHORIZATION_DENIED,
    S1_EVIDENCE_READ (unused).
    Grep of auditWriter.record(...) shows NO call from createCameraStream / updateCameraStream /
    createRoom/updateRoom/deleteRoom / createClass/.../ EventReviewService.confirm (which changes a
    detection event's status). These S1-adjacent surveillance/PII-config writes leave no actor trail.
  description: |
    The skill's audit checklist expects key CREATE/UPDATE/DELETE operations to land in audit_logs
    (actor/resource/action). Today only session lifecycle, role/membership state changes, and denials
    are audited. Mutations to surveillance config (camera stream source URL + encrypted credentials),
    room/class/camera topology, and detection-event review decisions (which can ESCALATE/RESOLVE a
    safety event and trigger guardian notifications) produce no SUCCESS audit record. For a children-
    safety + multi-tenant product this is a forensic/compliance gap: after an incident you cannot
    answer "who changed this camera's stream URL / who closed this event".
  recommendation: |
    Add SUCCESS audit records for tenant write paths (at minimum: camera stream create/update,
    event-review confirm, room/class/camera CRUD), reusing SecurityAuditWriter with resourceType +
    resourceId + actor + kindergartenId. Extend AuditAction accordingly. Keep best-effort/REQUIRES_NEW
    semantics already in place.
  confidence: high
```

```yaml
- id: SEC-02
  angle: security
  component: backend
  severity: medium
  title: AI service reads decrypted camera-stream credentials with no audit trail and no tenant scoping
  location: backend/src/main/java/com/ai_kids_care/v1/internal/StreamCredentialController.java:30-33 ↔ backend/src/main/java/com/ai_kids_care/v1/service/CameraStreamService.java:127-146
  evidence: |
    getStreamCredential(Long id): repository.findById(id)  // NO kindergarten filter (by design, OQ-3=B)
        ... AesGcmCryptoUtil.decrypt(...) -> returns plaintext RTSP password in StreamCredentialDTO
    Endpoint guarded only by the shared static Bearer token (ROLE_AI_SERVICE). No auditWriter.record(...)
    on this read path; S1_EVIDENCE_READ audit action exists but is documented as "no active call point".
  description: |
    The internal endpoint returns a decrypted camera password for ANY stream id across ALL tenants to
    a holder of one shared, long-lived Bearer token (AI_SERVICE_TOKEN). The decision to skip tenant
    isolation is intentional (AI is platform infra), but: (a) every decrypted-credential read is
    unlogged, so a leaked/abused AI token enumerating /internal/streams/{id}/credentials over all ids
    would exfiltrate every kindergarten's live-camera credentials with zero audit trace; (b) the token
    is static with no rotation/expiry mechanism in code (single value, both Java + AI runtimes). The
    blast radius of this one secret is "all surveillance credentials, silently".
  recommendation: |
    1) Audit each credential decryption read (actor=ai-service, resourceType=CAMERA_STREAM, resourceId,
       plus correlation id) — wire the already-present S1_EVIDENCE_READ action. 2) Plan token rotation
       (versioned tokens like the AES key already are) and document an expiry/rotation runbook. 3) Consider
       rate-limiting / id-range checks on the internal endpoint to blunt mass enumeration.
  confidence: high
  cross_refs: [SEC-01]
```

```yaml
- id: SEC-03
  angle: security
  component: backend
  severity: low
  title: Public registration-availability endpoint is a user-enumeration oracle (loginId / email / phone)
  location: backend/src/main/java/com/ai_kids_care/v1/controller/AuthController.java:214-218 ↔ backend/src/main/java/com/ai_kids_care/v1/service/AuthService.java:354-367 ; permitAll at SecurityConfig.java:88-93
  evidence: |
    GET /api/v1/auth/register/availability?field=email&value=foo@bar.com  (permitAll, no auth, no throttle)
    -> existsByEmailIgnoreCase(value) -> {available:false,"이미 사용 중인 이메일입니다."}
    Same for field=phone (existsByPhone) and field=login_id (existsByLoginId).
  description: |
    Unauthenticated callers can probe whether any email or phone number is a registered account.
    email/phone are S1 PII per the project's own audit masking rules. Unlike login throttling (which
    covers POST /auth/login), this GET has no rate limiting, so it can be scripted to enumerate a
    contact-list against the user base. Severity is low because it does not expose credentials or
    cross-tenant data, but it weakens privacy and aids credential-stuffing target selection.
  recommendation: |
    Apply the same Redis throttle used for login to this endpoint (per-IP), and/or return a uniform
    response that does not distinguish "exists" from "available" for email/phone (e.g. only signal on
    submit, server-side). At minimum rate-limit and log abusive scanning.
  confidence: high
```

```yaml
- id: SEC-04
  angle: security
  component: backend
  severity: low
  title: Public guardian-child verification confirms existence of a child by full RRN (existence oracle)
  location: backend/src/main/java/com/ai_kids_care/v1/controller/AuthController.java:204-209 ↔ backend/src/main/java/com/ai_kids_care/v1/service/AuthService.java:137-145 ; permitAll at SecurityConfig.java:94-98
  evidence: |
    POST /api/v1/auth/guardian-child-verifications  (permitAll)
    verifyGuardianChild -> childrenService.getChildEntityByRRN(first6, back7).isPresent()
    -> returns {verified: true|false}. No auth, no throttle on this endpoint.
  description: |
    An unauthenticated caller who supplies a child's full 13-digit RRN learns whether that child is
    enrolled in the platform (true/false). The full RRN requirement makes blind enumeration costly
    (13 digits, with a checksum), so this is low risk, but it is still an unauthenticated, unthrottled
    oracle over children PII and could confirm a known child's enrollment. No audit record is written
    for these probes.
  recommendation: |
    Rate-limit (per-IP) and audit these verification attempts; consider requiring a CAPTCHA or a
    short-lived signup token before the verification step so the oracle is not freely callable.
  confidence: high
  cross_refs: [SEC-03]
```

```yaml
- id: SEC-05
  angle: security
  component: backend
  severity: low
  title: Session cookie Secure flag defaults to false; CSRF cookie has no Secure binding
  location: backend/src/main/resources/application.yml:50-54 (secure: ${SESSION_COOKIE_SECURE:false}, same-site: lax) ; backend/src/main/java/com/ai_kids_care/v1/config/SecurityConfig.java:137-140 (CookieCsrfTokenRepository.withHttpOnlyFalse())
  evidence: |
    server.servlet.session.cookie.secure: ${SESSION_COOKIE_SECURE:false}
    @Bean CookieCsrfTokenRepository csrfTokenRepository() { return CookieCsrfTokenRepository.withHttpOnlyFalse(); }
    // no .setSecure(...) / cookie customizer; XSRF-TOKEN cookie inherits container default (not Secure)
  description: |
    The session cookie is httpOnly + SameSite=Lax (good) but Secure defaults to false; if a production
    deploy forgets to set SESSION_COOKIE_SECURE=true, the session id can be sent over plaintext HTTP.
    The .env.example documents that prod must set it, and the prod overlay runs behind Caddy TLS, so
    this is config-discipline rather than a code bug. Separately, the CSRF cookie (must be JS-readable
    for the SPA, so httpOnly=false is correct) is never marked Secure, so it can also traverse HTTP.
    Behind HTTPS-only edge + HSTS the practical risk is small.
  recommendation: |
    Make the prod profile enforce Secure (fail closed) rather than relying on an env default of false;
    bind the CSRF cookie's Secure flag to the same SESSION_COOKIE_SECURE value via a CookieCsrfToken
    customizer. Add an HSTS header at the edge (Caddy) if not already present.
  confidence: high
```

```yaml
- id: SEC-06
  angle: security
  component: backend
  severity: info
  title: CORS allowed-origins are a hardcoded localhost/demo list with allowCredentials=true
  location: backend/src/main/java/com/ai_kids_care/v1/config/SecurityConfig.java:148-164
  evidence: |
    configuration.setAllowedOrigins(Arrays.asList("http://localhost","http://localhost:80",
        "http://localhost:3000","http://127.0.0.1","http://127.0.0.1:3000","http://frontend"));
    configuration.setAllowCredentials(true);  // headers "*"
  description: |
    Origins are an explicit allow-list (not a wildcard) and allowCredentials=true is paired with
    concrete origins, so this is not the classic "* + credentials" bug. But the list is hardcoded to
    local/demo hosts; the real production origin (DOMAIN from the prod overlay) is absent, meaning the
    SPA either won't work cross-origin in prod or someone will widen this later under pressure. No
    direct vuln today — flagging so the production origin is added deliberately rather than by relaxing
    the matcher to a regex/wildcard.
  recommendation: |
    Externalize allowed origins to config and inject the production DOMAIN; never switch to
    setAllowedOriginPatterns("*") with allowCredentials=true.
  confidence: high
```

```yaml
- id: SEC-07
  angle: security
  component: backend
  severity: info
  title: Write operations rely solely on service-layer @PreAuthorize (no controller-level defense-in-depth) — verified covered, noted for regression risk
  location: backend/src/main/java/com/ai_kids_care/v1/controller/RoomController.java (POST/PUT/DELETE) ; ClassController, CameraStreamController (POST/PUT), AppreciationLetterController, AiModelController, EventReviewController — controllers carry no @PreAuthorize; gate lives on the service method
  evidence: |
    Controllers expose POST/PUT/DELETE with no @PreAuthorize; the @PreAuthorize sits on the @Service
    method (e.g. CameraStreamService.createCameraStream:70 TENANT_SURVEILLANCE_WRITE). EnableMethodSecurity
    proxies the service, so the gate IS enforced — but only because every controller method funnels
    through an annotated service method.
  description: |
    This is currently SAFE: every write controller delegates to a service method that carries the
    correct @PreAuthorize, and the tenant filter is enforced in the repository query. I attempted to
    refute "a write bypasses authz" and could not — each path is gated. The residual concern is
    regression-only: a future controller method that calls an un-annotated service helper (e.g. the
    legacy NotificationService.createNotification/updateNotification/deleteNotification at lines
    123/216/223 and getNotificationInternal:113 have NO @PreAuthorize and NO tenant filter) would be
    unprotected if ever wired to an endpoint. They are presently unreferenced by controllers.
  recommendation: |
    Add an ArchUnit/test guard asserting every public @Service method reachable from a controller has a
    @PreAuthorize, or annotate controllers too (belt-and-suspenders). Delete or @PreAuthorize("denyAll()")
    the legacy unscoped NotificationService CRUD helpers so they cannot be wired in unprotected later.
  confidence: high
```

```yaml
- id: SEC-08
  angle: security
  component: backend
  severity: info
  title: rrn_first6 (birth-date portion of RRN) stored in plaintext alongside the HMAC hash
  location: backend/src/main/java/com/ai_kids_care/v1/service/AuthService.java:163-166,219 (rrnFirst6(request.getRrnFirst6())) ; entities Guardian/Teacher rrnFirst6 field
  evidence: |
    Guardian.builder().rrnHash(guardianRrnHash).rrnFirst6(request.getRrnFirst6())...
    Teacher.builder().rrnHash(teacherRrnHash).rrnFirst6(request.getRrnFirst6())...
  description: |
    The first 6 RRN digits (birthdate YYMMDD) are persisted in clear, while the full RRN is only ever
    stored as an HMAC. Per the skill this is the intended minimization ("rrn_first6 display"), so this
    is an observation, not a defect: confirm rrn_first6 is treated as the maximum RRN exposure anywhere
    (VOs, Neo4j projection, logs) and that the back-7 is never logged or projected. Graph projection
    (GraphRepository) was checked and exposes only name/gender/status — no rrn/phone/email — consistent
    with the LoaderPiiProjectionGuardTest intent.
  recommendation: |
    Keep rrn_first6 as the only plaintext RRN fragment; add/keep a test asserting no VO, Neo4j node, or
    log line carries rrnBack7 / full RRN. (No change required if already enforced.)
  confidence: medium
```

## Explicitly checked and found OK (anti-false-positive notes)
- **Cross-tenant id → 404, not 403**: confirmed in ChildrenService:97, DetectionEventService (findByIdAndKindergarten_Id), CameraStreamService:63, NotificationService:87, EventReviewService:100. No existence oracle.
- **Internal ingest does not trust client tenant**: DetectionIngestService derives kindergarten_id/camera_id from stream/session server-side (lines 109-128), not from the AI request body → AI cannot inject arbitrary tenant.
- **Internal Bearer auth**: constant-time compare (MessageDigest.isEqual), no session persistence, ROLE_AI_SERVICE scoped to /api/v1/internal/** only, ordered before the wildcard authenticated() matcher; browser sessions get SESSION_AUTHENTICATED (not the role) → 403. CSRF-exempt only for /internal/** (token auth, not cookie) — justified.
- **AdminBootstrapRunner**: only acts when BOTH env vars set AND users table empty; rejects loginId 'admin'; never logs password; idempotent. Not a production backdoor.
- **Login throttle**: identifiers SHA-256-hashed in Redis keys; lock-before-password-check; generic 429; no identifier/password in audit. Single-instance caveat is documented (multi-instance is a known follow-up, not a finding here).
- **Secrets / fail-fast**: RRN pepper, AI service token, AES key(s), Pushover, Solapi all @NotBlank/@PostConstruct fail-fast; no defaults for sensitive values; .env.example placeholders only.
- **Injection**: all JdbcTemplate + JPQL parameterized; no concatenation sinks; graph path denyAll().
```
