# 后端分层骨架参考（Backend CRUD Layering Reference）

> 用途：这是从已弃用的 `pg-spring-crud-codegen/` 6 个 mustache 模板**回收并加固**而来的分层参考，供 agent 手写后端领域对象时对照。
>
> ⚠️ **为什么不直接用原模板**：原 codegen 量产的是「裸 CRUD」骨架，其默认产物**违反 SPEC-0001**（多租户 + 角色 scope）——它生成的 Controller 缺 `@Valid`/`@PreAuthorize`，Service 用 `findAll` 无租户过滤，VO 平铺所有字段（泄漏 S0/S1）。本文档保留它的**分层契约骨架**，同时把这些安全默认逐处纠正。
>
> 📌 **新增「租户/角色 scoped 读端点」请优先用 `authz-read-slice` skill**（`.claude/skills/authz-read-slice/SKILL.md`）——那是含 action+gate、scoped JPQL、审计拒绝、契约测试的完整执行清单。本文档是它的互补：逐文件骨架 + 反模式标注。
>
> ✅ 真实参考实现：`DetectionSession` 一线（`controller/service/repository/vo/mapper` 同名文件）、`Notification` read-slice（A3d 最新完整样例）。

---

## 分层契约（不可逾越）

```text
Controller  →  Service  →  Repository  →  (DB)
   入口          业务         数据访问
   薄层          授权+事务     scoped 查询
    │             │
    ↓ 出 VO       ↓ 入 DTO / 出 VO（经 MapStruct mapper）
```

| 层 | 职责 | 绝不允许 |
| --- | --- | --- |
| Controller | 仅编排：解析请求、调用 service、返回 `ResponseEntity` | ❌ 业务逻辑、❌ 授权判断、❌ 直接碰 Repository/Entity |
| Service | 业务逻辑、`@Transactional`、`@PreAuthorize` 方法级授权、role-branch | ❌ 在 Java 内做行级过滤（必须下推到 JPQL） |
| Repository | Spring Data JPA；**scoped** 查询（派生方法名或 `@Query`） | ❌ 无租户条件的 `findAll`/`findById` 直接对外 |
| Entity | JPA 映射 PG 表（须与表结构一致，否则 `ddl-auto=validate` 启动失败） | ❌ 直接作为传输对象暴露 |
| DTO | 输入模型（CreateDTO/UpdateDTO/请求 DTO） | — |
| VO | 输出模型（`record`），**只含调用方可见字段** | ❌ 平铺全字段、❌ 含 S0/S1 |
| Mapper | MapStruct `@Mapper(componentModel = "spring")` | — |

依赖注入统一 `@RequiredArgsConstructor` + `private final`（不要字段注入）。

---

## 逐文件骨架（骨架保留 ✅ / 护栏标注 ⚠️）

下面每个 `Xxx` 代表实体名（PascalCase），`xxx` 为资源路径（snake_case）。

### 1. VO（输出模型）

✅ 骨架（`record` + `Serializable`，来自原模板）：

```java
package com.ai_kids_care.v1.vo;

import java.io.Serializable;
import java.time.OffsetDateTime;

/** VO for {@link com.ai_kids_care.v1.entity.Xxx} */
public record XxxVO(
        Long xxxId,
        // ... 仅调用方可见字段
        OffsetDateTime createdAt
) implements Serializable {}
```

⚠️ **护栏（原模板默认错在「平铺所有字段」）**：
- VO 只列**调用方有权看到**的字段。**排除所有 S0/S1**：`rrnEncrypted` / `rrnHash` / `rrnFirst6`、`address`、`birthDate`、`staffNo`、`pushToken`、`dedupeKey`、`failReason`、内部 `userId`、以及其它内部标识符。
- 关系字段用扁平 id（如 `cameraId` 而非整个 `CctvCamera`），参照 `DetectionSessionVO`。
- 不确定某字段能否暴露时，默认**不放**，并在 PR 说明里标注待确认。

### 2. CreateDTO / UpdateDTO（输入模型）

✅ 骨架（Lombok `@Data` + 全参/无参构造，来自原模板）：

```java
package com.ai_kids_care.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/** DTO for {@link com.ai_kids_care.v1.entity.Xxx} */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class XxxCreateDTO implements Serializable {
    // 字段 + Bean Validation 约束
}
```

⚠️ **护栏（原模板默认无任何校验约束）**：
- 在字段上加 Bean Validation 注解（`@NotNull` / `@NotBlank` / `@Size` / `@Email` 等），并在 Controller 入参处用 `@Valid` 触发（见下）。
- DTO **绝不**接收由服务端身份决定的字段：如 `senderUserId`、`kindergartenId`、`authorId`、`role` ——这些一律从 `EffectiveAuthorizationContext` 推断，客户端传入即 IDOR 风险。

### 3. Mapper（MapStruct）

✅ 骨架：

```java
package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.entity.Xxx;
import com.ai_kids_care.v1.vo.XxxVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface XxxMapper {
    @Mapping(source = "id", target = "xxxId")
    // 关系扁平化：@Mapping(source = "relation.id", target = "relationId")
    XxxVO toVO(Xxx entity);
}
```

⚠️ **护栏（mapper gotcha，见 test-conventions §6）**：
- read VO 通常字段已收窄，`toVO` 只映射保留字段即可。
- 若实体含 NOT NULL `@ManyToOne` 关联且你保留了 `toEntity`/`updateEntity`（写映射），MapStruct 的 unmapped-target 策略会编译失败，需显式 `@Mapping(target = "relation", ignore = true)`。参照 `NotificationMapper`。
- 简单 read VO 也可在 Service 内私有方法手工组装（参照 `NotificationService.toReadVO`），不强制走 mapper。

### 4. Repository（scoped 数据访问）

⚠️ **这是原模板缺失最严重的一层** —— 原 Service 直接 `repository.findAll(pageable)`，等于跨租户全表可读。

✅ 正确做法 A — 派生方法名带租户路径（参照 `DetectionSessionRepository`）：

```java
public interface XxxRepository extends JpaRepository<Xxx, Long> {
    Page<Xxx> findAllBy<...>_Kindergarten_Id(Long kindergartenId, Pageable pageable);
    Optional<Xxx> findByIdAnd<...>_Kindergarten_Id(Long id, Long kindergartenId);
}
```

✅ 正确做法 B — 显式 `@Query`（参照 `NotificationRepository`，关系-scoped 用嵌套 `EXISTS`）：

```java
@Query("select x from Xxx x where x.kindergarten.id = :kgId order by x.createdAt desc, x.id desc")
List<Xxx> findKindergartenXxx(@Param("kgId") Long kgId);

@Query("select x from Xxx x where x.id = :id and x.kindergarten.id = :kgId and x.recipientUser.id = :userId")
Optional<Xxx> findRecipientXxx(@Param("id") Long id, @Param("kgId") Long kgId, @Param("userId") Long userId);
```

**铁律**：所有行级过滤写进查询，**不得**在 Java 里加载后过滤。

### 5. Service（授权 + 事务 + role-branch）

⚠️ 原模板的 Service 是**没有任何授权**的裸 CRUD。正确骨架（参照 `DetectionSessionService`）：

```java
@Service
@RequiredArgsConstructor
public class XxxService {

    private final XxxRepository repository;
    private final XxxMapper mapper;

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_XXX_READ)")
    public Page<XxxVO> listXxx(String keyword, Pageable pageable) {
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository.findAllBy<...>_Kindergarten_Id(kindergartenId, pageable).map(mapper::toVO);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@authorizationPolicy.isAllowed(T(com.ai_kids_care.v1.security.AuthorizationAction).TENANT_XXX_READ)")
    public XxxVO getXxx(Long id) {
        Long kindergartenId = EffectiveAuthorizationContextHolder.requireActiveKindergartenId();
        return repository.findByIdAnd<...>_Kindergarten_Id(id, kindergartenId)
                .map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Xxx not found"));
    }
}
```

⚠️ **护栏**：
- 每个对外方法都要 `@PreAuthorize("@authorizationPolicy.isAllowed(...AuthorizationAction.XXX)")`；新 action 在 `AuthorizationAction` + `AuthorizationPolicy` 中定义（粗粒度门只查 role+scope，行过滤在 Repository）。
- 租户 id 一律 `EffectiveAuthorizationContextHolder.requireActiveKindergartenId()`（为 null 自动 403），不要从入参取。
- 多角色时按 `context.role()` 做 role-branch（admin 看全园 / recipient 看自己），参照 `NotificationService` / `authz-read-slice` 步骤 3。
- 未命中一律 `throw new EntityNotFoundException(...)` → 隐藏 404，**不暴露存在性**（SPEC §3.4）；对越权访问写 `AUTHORIZATION_DENIED` 审计（见 skill 步骤 3）。
- read 方法必须 `@Transactional(readOnly = true)`；避免在 read 方法里做写操作（如 viewCount 自增——若需要，用 `@Modifying` 原子 UPDATE，勿读改写）。

### 6. Controller（薄层，无授权逻辑）

✅ 骨架（参照 `DetectionSessionController`）：

```java
@Tag(name = "Xxx")
@RestController
@RequestMapping("/api/v1/xxx")
@RequiredArgsConstructor
public class XxxController {

    private final XxxService service;

    @GetMapping
    public ResponseEntity<Page<XxxVO>> listXxx(
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.listXxx(keyword, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<XxxVO> getXxx(@PathVariable Long id) {
        return ResponseEntity.ok(service.getXxx(id));
    }

    // 写操作（如开放）：
    @PostMapping
    public ResponseEntity<XxxVO> createXxx(@Valid @RequestBody XxxCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createXxx(dto));
    }
}
```

⚠️ **护栏**：
- 写入端点的 `@RequestBody` **必须**配 `@Valid`（原模板默认漏了），否则校验不触发。
- Controller 不写 `@PreAuthorize`、role 检查、业务逻辑——全在 Service。
- 写操作若暂不开放：**不要**加 handler（路径存在但无写 handler → 405，优于显式拒绝；契约测试依赖这一点）。
- `@RequestMapping` 必须在 `/api/v1/**` 下，并加 `@Tag` 供 OpenAPI 分组。

---

## 护栏检查清单（SPEC-0001 自检）

提交一个新端点前逐条核对：

- [ ] VO 不含任何 S0/S1 字段（RRN/地址/出生日期/staffNo/pushToken/内部 userId…）
- [ ] DTO 不接收服务端身份决定的字段（kindergartenId/senderUserId/role…）
- [ ] 写端点 `@RequestBody` 有 `@Valid`，DTO 字段有 Bean Validation 约束
- [ ] Service 每个对外方法有 `@PreAuthorize`，对应 action 已在 `AuthorizationPolicy` 连接
- [ ] 租户 id 来自 `EffectiveAuthorizationContextHolder`，不来自入参
- [ ] 行级过滤全部在 Repository 查询里（无 Java 层 load-then-filter）
- [ ] 未命中走 `EntityNotFoundException` 隐藏 404，越权写 `AUTHORIZATION_DENIED` 审计
- [ ] read 方法 `@Transactional(readOnly = true)`
- [ ] Controller 不含授权/业务逻辑；未开放的写操作不加 handler
- [ ] 三个契约测试（`SensitivePublicApiClosureContractTest` / `SensitiveWriteContractTest` / `PublishedOpenApiContractTest`）已同步
- [ ] 加了 `XxxReadAuthorizationIntegrationTest`（镜像 `NotificationReadAuthorizationIntegrationTest`）

---

## 指针

- 完整读端点执行清单（强烈推荐）：`.claude/skills/authz-read-slice/SKILL.md`
- 真实样例：`DetectionSession*`（tenant-scoped 读）、`Notification*` + `NotificationReadAuthorizationIntegrationTest`（含 role-branch + 审计 + 契约测试）
- schema 事实：[`schema-digest.md`](schema-digest.md)（NOT NULL/UNIQUE/FK/enum）
- 测试陷阱：[`test-conventions.md`](test-conventions.md)
- 分层契约背景：[ADR-0004](../decisions/adr/ADR-0004-layered-backend-codegen.md)（codegen 弃用决策见后续 superseding ADR）
