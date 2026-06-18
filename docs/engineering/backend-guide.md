# 后端开发指南（Backend Guide）

✅ 来源：`backend/`。架构总览见 [architecture/backend-architecture.md](../architecture/backend-architecture.md)。

## 包结构速查

```text
com.ai_kids_care.v1
├── controller   # REST 入口，/api/v1/**
├── service      # 业务逻辑，@Transactional
├── repository   # Spring Data JPA + GraphRepository(Neo4j Cypher)
├── entity       # JPA 实体（映射 PG 表）
├── dto          # 输入模型（CreateDTO/UpdateDTO/请求 DTO）
├── vo           # 输出模型（VO；vo/graph 为图结果）
├── mapper       # MapStruct 接口
├── type         # 领域枚举（对应 PG enum）
├── security     # JwtUtil / JwtAuthenticationFilter / AesGcmCryptoUtil
└── config       # SecurityConfig / Neo4jConfig
```

## 既有约定（务必遵循）

| 约定 | 怎么做 |
| --- | --- |
| 分层不可逾越 | Controller 只编排，不写业务；业务在 Service；数据访问在 Repository |
| 传输层不暴露 Entity | 入参用 DTO，出参用 VO，转换走 MapStruct mapper |
| 依赖注入 | `@RequiredArgsConstructor` + `private final` 字段（不要字段注入） |
| 分页 | 列表接口用 `Pageable` + `@PageableDefault(size=20)`，返回 `Page<XxxVO>` |
| OpenAPI | Controller 上加 `@Tag`，便于 Swagger 分组 |
| 枚举 | PG enum ↔ `type/` 包枚举一一对应 |

## 新增后端领域对象

`pg-spring-crud-codegen` 代码生成器已由 [ADR-0027](../decisions/adr/ADR-0027-retire-pg-spring-crud-codegen.md) 退役（2026-06-18）。

新增领域对象改用**手写 + 分层参考**：

- 分层骨架与安全默认：[`docs/engineering/backend-crud-layering-reference.md`](backend-crud-layering-reference.md)（含 `@PreAuthorize`、租户过滤、VO 字段收窄等加固）
- 含完整 authz gate + scoped JPQL + 契约测试的读端点：`.claude/skills/authz-read-slice/SKILL.md`

**新增一个领域对象的典型步骤**：

1. 在 `db/dbml/schema.dbml` 增表 → 生成 SQL（见 [database-guide](database-guide.md)）。
2. 参照 [backend-crud-layering-reference.md](backend-crud-layering-reference.md) 手写骨架（codegen 已退役，见 ADR-0027）。
3. 在 `entity/` 写/校对 JPA 实体（必须与表结构一致，否则 `ddl-auto=validate` 启动失败）。
4. 补 `repository`/`service` 业务逻辑、`mapper` 映射、`dto`/`vo`。
5. Controller 暴露 `/api/v1/...`。
6. 更新 [api/rest-endpoints.md](../api/rest-endpoints.md) 与相关文档。
7. 补测试，见 [testing](testing.md)。

## 访问 Neo4j

✅ 图查询不用 Spring Data Neo4j，而是 `Neo4jConfig` 装配官方 `Driver`，在 `GraphRepository` 写**原生 Cypher**。参考 `findChildGraph` 的写法（`executeRead` + 手工 `Record→VO` 映射）。

## 注意事项 / 陷阱

- ⚠️ **鉴权已启用**（默认拒绝 + 服务端会话，[security-architecture](../architecture/security-architecture.md)）：调 `/api/v1/**` 需先 `/auth/login` 拿会话 cookie（`AI_KIDS_CARE_SESSION`）+ CSRF（`GET /auth/csrf` → `X-XSRF-TOKEN` 头）。后端测试需 Docker（Testcontainers：Postgres + Redis）。
- `logging.level.root: ${LOG_LEVEL_ROOT:INFO}`：安全默认 INFO；调试设 `LOG_LEVEL_ROOT=DEBUG`。
- ⚠️ 无统一异常处理器：service 抛 `RuntimeException`/`IllegalArgumentException`，错误响应格式不统一。
- ⚠️ 无测试基线：改动后请手工验证关键流程。
