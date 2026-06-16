---
name: authz-read-slice
description: Scaffold a tenant/role-scoped authorization READ endpoint (action+gate, scoped JPQL, service role-branch, minimal read VO, MapStruct, integration test) following this repo's SPEC-0001 pattern. Use when adding a new read endpoint that returns rows a caller may only see within their kindergarten/role scope.
---

本 Skill 为「authz read slice」模式的执行清单。已有三个完整样例可供对照：
- **T2 Guardian→child read**：`ChildrenService.listRelatedChildren` / `getRelatedChild`（SPEC-0001 §349）
- **A3a Teacher→child read**：`ChildrenService`（SPEC-0001 §351）
- **A3d Notification read**（最新完整样例）：`NotificationController` / `NotificationService` / `NotificationRepository` / `NotificationReadVO` / `NotificationReadAuthorizationIntegrationTest`

每次执行前先读 [`docs/engineering/schema-digest.md`](../../docs/engineering/schema-digest.md)（NOT NULL / UNIQUE / FK / enum）和 [`docs/engineering/test-conventions.md`](../../docs/engineering/test-conventions.md)（fixture 陷阱）。

---

## 执行清单

### 步骤 1 — 在 `AuthorizationAction` 枚举中添加新 action，并在 `AuthorizationPolicy` 中连接粗粒度门

**文件：**
- `backend/src/main/java/com/ai_kids_care/v1/security/AuthorizationAction.java`
- `backend/src/main/java/com/ai_kids_care/v1/security/AuthorizationPolicy.java`

**操作：**

1. 在 `AuthorizationAction.java` 末尾（或逻辑分组处）追加新枚举值，附注释说明作用域规则，例如：

   ```java
   // SPEC-XXXX / ADR-XXXX：<资源>读取粗粒度门——<允许的 role 列表> + 有效 tenant identity；
   // 细粒度「<作用域约束>」由 <XxxRepository> SQL 强制。
   YOUR_RESOURCE_READ
   ```

2. 在 `AuthorizationPolicy.isAllowed` 的 `switch` 中新增 case。**必须**遵循现有模式：先断言 `tenantIdentity`（`scopeType == KINDERGARTEN && activeKindergartenId != null`），再检查允许的 `role`：

   ```java
   case YOUR_RESOURCE_READ ->
       tenantIdentity && (role == UserRoleEnum.GUARDIAN
               || role == UserRoleEnum.TEACHER
               || role == UserRoleEnum.KINDERGARTEN_ADMIN);
   ```

   如需 PLATFORM scope（无 tenant），仿照 `PLATFORM_SUPERADMIN_APPROVAL_READ` case。

   **关键**：粗粒度门只做 role + scope 检查；细粒度行过滤在 Repository SQL 内完成，不在 Policy 内。

**连接方式**：Service 方法用 `@PreAuthorize` 注解——

```java
@PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).YOUR_RESOURCE_READ)")
```

`authorizationPolicy` bean 是 `AuthorizationPolicy`（`@Component("authorizationPolicy")`）；表达式由 Spring Security 在方法调用前执行。无需修改 `SecurityConfig`；`/api/v1/**` 的 `permitAll` 令 session 验证和 CSRF 在 Spring Security filter chain 内完成，`@PreAuthorize` 做方法级授权。

---

### 步骤 2 — 在 Repository 中写细粒度 scoped JPQL 查询

**文件：** `backend/src/main/java/com/ai_kids_care/v1/repository/<YourResource>Repository.java`

所有行级过滤**必须**写进 JPQL `@Query`，不得在 Java 代码中加载后过滤。每个方法一个 `@Query`；只用 `@Param` 具名参数。

**标准子句（必须包含）：**
- 租户隔离：`n.<association>.id = :kgId`（参照 `NotificationRepository` 的 `n.kindergarten.id = :kindergartenId`）
- 作用域约束：recipient-scoped（`n.recipientUser.id = :userId`）或 admin-scoped（仅 kgId）

**最小样例（recipient-scoped 列表 + 详情 + admin-scoped 列表 + 详情，共 4 个方法）：**

```java
// 受体列表
@Query("select r from YourResource r where r.kindergarten.id = :kgId and r.recipientUser.id = :userId order by r.createdAt desc, r.id desc")
List<YourResource> findRecipientResources(@Param("kgId") Long kgId, @Param("userId") Long userId);

// 受体详情
@Query("select r from YourResource r where r.id = :id and r.kindergarten.id = :kgId and r.recipientUser.id = :userId")
Optional<YourResource> findRecipientResource(@Param("id") Long id, @Param("kgId") Long kgId, @Param("userId") Long userId);

// Admin 列表
@Query("select r from YourResource r where r.kindergarten.id = :kgId order by r.createdAt desc, r.id desc")
List<YourResource> findKindergartenResources(@Param("kgId") Long kgId);

// Admin 详情
@Query("select r from YourResource r where r.id = :id and r.kindergarten.id = :kgId")
Optional<YourResource> findKindergartenResource(@Param("id") Long id, @Param("kgId") Long kgId);
```

关系-scoped 查询（Guardian/Teacher）参照 `ChildRepository.findRelatedChildrenForGuardian` / `findActivelyAssignedChildrenForTeacher`，用嵌套 `EXISTS` 子查询；**不要**把窗口条件放到 Java 层。

---

### 步骤 3 — Service 层 role-branch + `@PreAuthorize` + 审计拒绝

**文件：** `backend/src/main/java/com/ai_kids_care/v1/service/<YourResource>Service.java`

**模式（镜像 `NotificationService.listNotifications` / `getNotification`）：**

```java
@Transactional(readOnly = true)
@PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).YOUR_RESOURCE_READ)")
public List<YourResourceReadVO> listResources() {
    EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
    Long kgId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
    List<YourResource> rows = context.role() == UserRoleEnum.KINDERGARTEN_ADMIN
            ? repository.findKindergartenResources(kgId)
            : repository.findRecipientResources(kgId, context.userId());
    return rows.stream().map(this::toReadVO).toList();
}

@Transactional(readOnly = true)
@PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).YOUR_RESOURCE_READ)")
public YourResourceReadVO getResource(Long id) {
    EffectiveAuthorizationContext context = EffectiveAuthorizationContextHolder.require();
    Long kgId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
    Optional<YourResource> found = context.role() == UserRoleEnum.KINDERGARTEN_ADMIN
            ? repository.findKindergartenResource(id, kgId)
            : repository.findRecipientResource(id, kgId, context.userId());
    if (found.isEmpty()) {
        auditWriter.record(AuditEvent.builder()
                .action(AuditAction.AUTHORIZATION_DENIED)
                .result(AuditResult.DENIED)
                .actorUserId(context.userId())
                .scopeType(UserRoleAssignmentScopeType.KINDERGARTEN)
                .kindergartenId(kgId)
                .effectiveRole(context.role().name())
                .resourceType("YOUR_RESOURCE")      // 大写，与 audit_logs.resource_type 一致
                .resourceId(id)
                .build());
        throw new EntityNotFoundException("YourResource not found");
    }
    return toReadVO(found.get());
}
```

`SecurityAuditWriter.record` + `EntityNotFoundException`（→ 全局异常处理器→ 隐藏 404 `{"error":"Resource not found"}`）是 SPEC §3.4 的实现方式；不要直接 return 404，不要暴露存在性信息。

---

### 步骤 4 — 最小 read VO + MapStruct mapper 处理

**文件：**
- `backend/src/main/java/com/ai_kids_care/v1/vo/<YourResource>ReadVO.java`
- `backend/src/main/java/com/ai_kids_care/v1/mapper/<YourResource>Mapper.java`

**Read VO 规则**：只含调用方可见的字段，排除所有 S0/S1 字段（`rrnEncrypted`、`rrnFirst6`、`address`、`birthDate`、`pushToken`、`dedupeKey`、`failReason`、`recipientUserId`、`kindergartenId`……）。用 Java `record`：

```java
public record YourResourceReadVO(
        Long resourceId,
        String title,
        // ... 最小字段
        java.time.OffsetDateTime createdAt
) implements Serializable {}
```

**MapStruct**：read VO 通常在 Service 内通过简单构造或私有方法组装，不走 mapper（参照 `NotificationService.toReadVO`）。若用 mapper，read 方法不受影响。

**必须处理的 mapper gotcha（test-conventions §6）**：若实体新增了一个 NOT NULL `@ManyToOne` 关联（如 `Notification.kindergarten`），MapStruct 在现有的 `toEntity`/`updateEntity` 闭合写 mapper 方法中会检测到未映射的 target，编译警告甚至错误。需在对应方法上明确 `ignore`：

```java
@Mapping(target = "kindergarten", ignore = true)
Notification toEntity(NotificationCreateDTO dto);

@Mapping(target = "kindergarten", ignore = true)
void updateEntity(NotificationUpdateDTO dto, @MappingTarget Notification entity);
```

参照 `NotificationMapper.java`。

---

### 步骤 5 — Controller 层（薄层，无授权逻辑）

**文件：** `backend/src/main/java/com/ai_kids_care/v1/controller/<YourResource>Controller.java`

```java
@Tag(name = "YourResource")
@RestController
@RequestMapping("/api/v1/your_resources")
@RequiredArgsConstructor
public class YourResourceController {
    private final YourResourceService yourResourceService;

    @GetMapping
    public ResponseEntity<List<YourResourceReadVO>> listResources() {
        return ResponseEntity.ok(yourResourceService.listResources());
    }

    @GetMapping("/{id}")
    public ResponseEntity<YourResourceReadVO> getResource(@PathVariable Long id) {
        return ResponseEntity.ok(yourResourceService.getResource(id));
    }
}
```

Controller 不含 `@PreAuthorize`、role 检查、或业务逻辑——全部在 Service。写操作如暂不开放，不要加 handler（路径存在但无写 handler → 405，优于显式拒绝）。

---

### 步骤 6 — 三个契约测试需要同步更新

添加一个新的 tenant-scoped GET 端点后，必须更新以下三个契约测试；各测试均为轻量单元测试（无容器），运行极快。

**6a. `SensitivePublicApiClosureContractTest`**
文件：`backend/src/test/java/com/ai_kids_care/v1/contract/SensitivePublicApiClosureContractTest.java`

`graphAuditLogNotificationAndSensitiveControllersPublishNoOperations` 若之前断言你的 Controller 无方法，需更新（添加注释说明端点已重开），或从断言列表中删除。`controllersWithoutPublishedOperationsReturn404ForRepresentativePaths` 的 MockMvc setup 也对应调整（参照 Notification/Children 的现有注释）。

**6b. `SensitiveWriteContractTest`**
文件：`backend/src/test/java/com/ai_kids_care/v1/contract/SensitiveWriteContractTest.java`

在 `closedPublicWriteEndpointsDoNotCallServices` 中：
- 将 `YourResourceController(yourResourceService)` 加入 `standaloneSetup(...)`
- 断言写操作返回 405（路径存在但无写 handler）：

  ```java
  // your_resources 已重开 GET（tenant-scoped）；写操作仍未发布 → 405。
  mockMvc.perform(post("/api/v1/your_resources").contentType("application/json").content("{}"))
          .andExpect(status().isMethodNotAllowed());
  mockMvc.perform(put("/api/v1/your_resources/1").contentType("application/json").content("{}"))
          .andExpect(status().isMethodNotAllowed());
  mockMvc.perform(delete("/api/v1/your_resources/1"))
          .andExpect(status().isMethodNotAllowed());
  ```

- 将 `yourResourceService` 加入 `verifyNoInteractions(...)` 列表。

**6c. `PublishedOpenApiContractTest`**
文件：`backend/src/test/java/com/ai_kids_care/v1/contract/PublishedOpenApiContractTest.java`

在 `publishedOpenApiDoesNotExposeRemovedSensitiveFieldsOrClosedEndpoints` 中：
- 断言完整的旧 VO 已从 OpenAPI 组件移除：`assertComponentAbsent(apiDocs, "YourResourceVO")`
- 断言新 read VO 只暴露最小字段：

  ```java
  // SPEC-XXXX：只发布最小 YourResourceReadVO（无 <S0/S1 字段列表>）。
  assertComponentHasOnlyProperties(apiDocs, "YourResourceReadVO", Set.of(
          "resourceId", "title", /* 其余允许字段 */
          "createdAt"
  ));
  ```

- 断言 GET 端点已发布：

  ```java
  assertOperationPresent(apiDocs, "/api/v1/your_resources", "get");
  assertOperationPresent(apiDocs, "/api/v1/your_resources/{id}", "get");
  ```

注意：`PublishedOpenApiContractTest` 会自动扫描 `com.ai_kids_care.v1.controller` 包下所有 `@RestController` 并断言它们的每个 handler 均出现在 OpenAPI 文档中（`publishedOpenApiIncludesEveryV1ControllerOperation`）。新 Controller 注册后该测试自动覆盖。

---

### 步骤 7 — 集成测试

**文件：** `backend/src/test/java/com/ai_kids_care/v1/security/<YourResource>ReadAuthorizationIntegrationTest.java`

**必须覆盖的用例（镜像 `NotificationReadAuthorizationIntegrationTest`）：**

| 测试场景 | 预期结果 |
|----------|----------|
| 受体读自己的资源（列表 + 详情） | 200，只含最小字段；列表精确，不含他人资源 |
| 受体读同租户他人资源详情 | 404 `{"error":"Resource not found"}` + audit_logs 中有 `AUTHORIZATION_DENIED` + `DENIED` 记录 |
| KINDERGARTEN_ADMIN 读取本园全部资源（列表 + 详情） | 200 |
| 未认证请求 | 401 |
| 有别的角色无权访问此 action（如 KINDERGARTEN_ADMIN 对 CHILD_READ）| 403（粗粒度门拒绝） |

**跨租户 404**：优先用「同租户他人资源」路径（test-conventions §2：composite FK 使得跨外园插数据代价很高）。`kindergarten.id = :kgId` 的 SQL 过滤是同一个子句，覆盖效果等价。

**audit_logs 断言写法（镜像 `NotificationReadAuthorizationIntegrationTest.getNotification_otherRecipientSameTenant_returnsHidden404AndAuditsDenied`）：**

```java
String correlationId = result.getResponse().getHeader("X-Correlation-Id");
assertThat(correlationId).isNotBlank();
Integer denied = jdbc.queryForObject(
        "SELECT count(*) FROM audit_logs "
                + "WHERE correlation_id = ? AND action = 'AUTHORIZATION_DENIED' "
                + "AND result = 'DENIED' AND resource_type = 'YOUR_RESOURCE' "
                + "AND resource_id = ? AND user_id = ?",
        Integer.class, correlationId, resourceId, userIdOf(RECIPIENT_LOGIN));
assertThat(denied).isEqualTo(1);
```

**login_id 前缀和 phone 前缀必须全局唯一（test-conventions §1）。**
在选择前先 grep：`grep -r "010-0" backend/src/test`，参照已有前缀表：

| 测试类 | login 前缀 | phone 前缀 |
|--------|-----------|-----------|
| GuardianChildAuthorizationIntegrationTest | `gc-*` | `010-0700-*` |
| TeacherChildAuthorizationIntegrationTest | `tc-*` | `010-0800-*` |
| NotificationReadAuthorizationIntegrationTest | `nr-*` | `010-0905-*` |

选一个未被占用的前缀并在此表中登记（更新 `test-conventions.md`）。

**test fixtures 注意事项（test-conventions §3/§7/§8）：**
- enum 绑定参数需显式 cast：`?::your_status_enum`
- `@AfterEach` 按 FK 顺序清理（关联行先删，父行后删），并删 `audit_logs WHERE resource_id = ?`
- KINDERGARTEN_ADMIN fixture 只需 role + membership，不需 teachers 档案（test-conventions §4）
- 用 `ON CONFLICT (login_id) DO UPDATE` 实现 upsertUser，确保 email/phone 在本类内唯一

---

### 步骤 8 — 本地验证

```bash
# 最快（仅编译，捕获类型/MapStruct 错误）
bash scripts/test-backend.sh --compile

# 运行新测试类
bash scripts/test-backend.sh '*YourResourceReadAuthorizationIntegrationTest*'

# 运行三个契约测试
bash scripts/test-backend.sh '*ContractTest*'

# 如有新 Flyway migration，重新生成 schema-digest
bash scripts/schema-digest.sh

# push 前运行完整后端套件
bash scripts/test-backend.sh
```

如无新 migration，不需要重新生成 schema-digest。

---

## 非显而易见的陷阱（高密度提示）

- **粗粒度门不做行过滤**：`AuthorizationPolicy` 只检查 role + scope；行级作用域必须在 Repository JPQL 的 `WHERE` 子句中。两层不要交叉。
- **`EntityNotFoundException` → 隐藏 404**：全局异常处理器将其转换为 `{"error":"Resource not found"}`，绝不暴露资源是否存在（SPEC §3.4）。不要直接 `throw new ResponseStatusException(404, ...)`。
- **`EffectiveAuthorizationContextHolder.requireActiveKindergartenId()`**：返回已验证的 `activeKindergartenId`，若 null 则 403。不要再次 null 检查；直接用此返回值作为 JPQL `:kgId` 参数。
- **`@Transactional(readOnly = true)`**：read slice 必须加，避免不必要的写锁。
- **MapStruct `ignore` 规则（test-conventions §6）**：任何已有的 closed write mapper（`toEntity`/`updateEntity`）发现实体新增 NOT NULL `@ManyToOne` 后都需要 `@Mapping(target = "...", ignore = true)`，否则编译失败（MapStruct 的 unmapped-target 策略）。
- **契约测试是 Phase-1A 安全快照**：`SensitivePublicApiClosureContractTest` / `SensitiveWriteContractTest` / `PublishedOpenApiContractTest` 三个测试共同锁定哪些端点/VO/字段可以对外可见。新增 slice 后三个均需更新，否则 `PublishedOpenApiContractTest.publishedOpenApiIncludesEveryV1ControllerOperation` 会因为 Controller 存在但 OpenAPI assert 未更新而失败。
