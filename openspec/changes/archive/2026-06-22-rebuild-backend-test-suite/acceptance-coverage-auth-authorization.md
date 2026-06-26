# auth-authorization 验收覆盖映射

把 `openspec/specs/auth-authorization/spec.md` 的每条 requirement 映射到覆盖它的后端测试。
所有被引用的测试类均真实存在并在本 change 的套件中通过（容器内 `./gradlew test` 实跑：
**132 tests / 2 skipped / 0 failures / 0 errors**，2 skipped 为 ADR-0013 阻塞的 `@Disabled` fresh-Flyway 占位）。

测试根：`backend/src/test/java/com/ai_kids_care/`

| auth-authorization Requirement | 覆盖测试类 → 代表方法 |
|---|---|
| Server-Side Session Authentication | `v1.auth.AuthEndpointTest` → `login_validCredentials_returnsSessionProfileWithoutBearerTokens`, `login_wrongPassword_returns401`, `csrfEndpoint_returnsReadableCookieAndHeaderContract` |
| Default-Deny Authentication Boundary | `v1.security.SecurityBoundaryIntegrationTest` → `anonymousAccessToPublishedBusinessEndpointsIsUnauthorized`, `anonymousAccessToPublicWhitelistIsAllowed`, `anonymousAccessToClosedControllersIsUnauthorizedNotAnExistenceOracle`；`AuthEndpointTest` → `refresh_isClosed`, `session_withoutLogin_returns401` |
| Effective Authorization Context Per Request | `AuthEndpointTest` → `authenticatedRequest_afterRoleRevocationReturns401AndInvalidatesSession`, `authenticatedRequest_afterUserDisabledReturns401`, `authenticatedRequest_afterMembershipEndedReturns401` |
| Tenant Isolation by kindergarten_id | `v1.security.TenantIsolationIntegrationTest` → `kindergartenAdminCannotReadForeignTenantResourcesByValidId`, `adminWriteRejectsForeignTenantKindergartenOverride`, `kindergartenAdminListSeesOnlyOwnTenantRoomsAndOwnRoomIsReachable`；`AuthEndpointTest` → `tenantClassQueries_hideForeignTenantResources`, `tenantWrite_rejectsClientKindergartenOverride`, `cameraList_rejectsClientKindergartenOverride` |
| Role-Based Access Control with Centralized Policy | `v1.security.GuardianChildAuthorizationIntegrationTest` → `children_kindergartenAdminRole_returns403`；`AuthEndpointTest` → `teacher_cannotReadSurveillanceResources`, `kindergartenRole_cannotReadPlatformAiMetadata` |
| Authz Read-Slice Pattern | `GuardianChildAuthorizationIntegrationTest` → `getRelatedChild_crossTenantChild_returnsHidden404`；`AuthEndpointTest` → `tenantClassQueries_hideForeignTenantResources` |
| Guardian Child Relationship Boundary | `GuardianChildAuthorizationIntegrationTest` → `getRelatedChild_activeRelationship_returnsMinimalFields`, `getRelatedChild_noRelationship_returnsHidden404AndAuditsDenied`, `getRelatedChild_endedRelationship_returnsHidden404`, `listRelatedChildren_returnsOnlyActiveRelationshipChildren` |
| Teacher Assignment Boundary | `v1.security.TeacherAssignmentAuthorizationIntegrationTest`, `v1.security.TeacherChildAuthorizationIntegrationTest`, `v1.security.TeacherRoomAssignmentAuthorizationIntegrationTest`（覆盖班级/房间/儿童分配窗口 + 教师不可读监控） |
| Anonymous Registration Produces PENDING-Only Records | `v1.service.AuthServiceRegistrationTest` + `AuthEndpointTest` → `register_guardianRole_createsPendingProfileRoleAndMembership`, `register_kindergartenRoles_createPendingProfileRoleAndMembership`, `register_superadminRole_createsPendingApplicationAndCannotLogin`, `register_platformItAdminRole_isRejectedBeforePersistence`, `register_kindergartenRoleLevelMismatch_isRejectedBeforePersistence` |
| Approval Requires Authorized KINDERGARTEN_ADMIN Approver | `v1.admin.AdminApprovalAuthorizationIntegrationTest`, `v1.admin.PlatformAdminApprovalAuthorizationIntegrationTest`（director/vice-director 才能批、禁自批、跨租户禁批） |
| Single Account Single Role | `AuthEndpointTest` → `login_activeUserWithoutActiveRole_returns401`, `login_kindergartenScopedRoleWithoutMembership_returns401`, `login_platformRoleWithActiveMembership_returns401`；INC-001：`v1.auth.PhoneUniquenessConstraintTest` → `secondAccountWithSamePhoneIsRejectedByUniqueConstraint` |
| Session Revocation on State Changes | `AuthEndpointTest` → `logout_invalidatesCurrentSession`, `logoutAll_revokesEverySessionForTheUser`, `authenticatedRequest_afterMembershipEndedReturns401` |
| Platform Role Tenant Context Selection | `AuthEndpointTest` → `platformRole_selectsValidatedTenantContextWithoutChangingRole`, `kindergartenRole_cannotSelectPlatformTenantContext`, `platformTenantSelection_doesNotGrantCameraRead` |
| Authentication Failure Response Contracts | `v1.security.ErrorResponseSensitiveDataIntegrationTest` → `unauthenticatedError_exposesNoSensitiveData`, `malformedRequestBody_errorResponseExposesNoSecretsOrInternals`, `registerValidationError_doesNotEchoSubmittedSecrets`, `csrfRejectedLoginError_doesNotEchoSubmittedPassword` |
| Security Audit Logging | `v1.security.SecurityAuditIntegrationTest`（登录成功/失败、授权拒绝、平台事件不伪造 kindergarten_id 等）；`GuardianChildAuthorizationIntegrationTest` → `getRelatedChild_noRelationship_returnsHidden404AndAuditsDenied` |
| Frontend Must Not Infer Tenant or Role from Client State | N/A（前端关注点，不在后端测试范围；由前端测试与会话契约保证） |
| Production Session Hardening Prerequisites | N/A（部署配置，由 `compose-config` CI 校验生产 compose 的 `SESSION_COOKIE_SECURE`/HTTPS） |

## 迁移路径冒烟（testing-and-ci）

| 关注点 | 测试 |
|---|---|
| initdb + Flyway baseline(V1)+V2–V6 + ddl-auto=validate 真实部署路径 | `smoke.FlywayMigrationSmokeTest`, `smoke.ContextLoadSmokeTest` |
| fresh-Flyway（空库 V1 建全 schema） | `FlywayMigrationTest`（`@Disabled`，ADR-0013 阻塞，见 tasks.md §2） |

## 已知缺口（本切片不覆盖，归其他能力的后续 change）

- announcements / appreciation-letters / notifications / 监控流凭据 / RRN 哈希 / ai-detection / OpenAPI 契约等授权测试 —— 这些被删测试归属各自能力，按 testing-and-ci「按能力增量 TDD」原则随各能力 change 重建。
- **Guardian-child「拒绝监控访问」scenario 无专属测试**（spec Guardian Child Relationship Boundary 下「Guardian is denied surveillance access」）：当前由默认拒绝 + RBAC 矩阵（仅 KINDERGARTEN_ADMIN 可达 cameras）间接保证，回归风险低；专属 GUARDIAN→403 监控端点测试留作下一个 auth 相关 change 补齐（code review m-3）。
- ADR-0013 决议后启用 `FlywayMigrationTest`（fresh-Flyway 路径）。
