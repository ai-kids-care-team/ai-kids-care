# 后端架构（Backend Architecture）

✅ 主要来源：`backend/src/main/java/com/ai_kids_care/v1/`、`backend/build.gradle`、`backend/src/main/resources/application.yml`。

## 1. 技术栈

- **Java 21**，**Spring Boot 3.2.5**（`org.springframework.boot` 插件）。
- Spring Web（REST）、Spring Security、Spring Data JPA、Bean Validation。
- **MapStruct 1.5.5**（编译期 DTO↔Entity↔VO 映射）+ **Lombok**。
- **springdoc-openapi 2.6**（Swagger UI）。
- **jjwt 0.12.3**（JWT）。
- **Neo4j Java Driver 5.19**（图查询，非 Spring Data Neo4j）。
- **PostgreSQL JDBC 42.7.3**。

## 2. 分层架构

✅ 严格的经典分层，所有业务模块同构：

```text
HTTP 请求
   │
   ▼
┌─────────────┐   @RestController, /api/v1/**, 只做请求/响应编排
│ controller  │   入参 DTO，出参 VO，返回 ResponseEntity
└──────┬──────┘
       ▼
┌─────────────┐   @Service, @Transactional, 业务逻辑
│  service    │   调用 repository + mapper
└──────┬──────┘
       ▼
┌─────────────┐   Spring Data JPA Repository（PG）
│ repository  │   + GraphRepository（Neo4j 原生 Cypher）
└──────┬──────┘
       ▼
┌─────────────┐   @Entity（JPA 映射 PG 表）
│   entity    │
└─────────────┘

横切：
- dto/    输入模型（XxxCreateDTO / XxxUpdateDTO / 各种请求 DTO）
- vo/     输出模型（XxxVO；vo/graph/ 为图查询结果）
- mapper/ MapStruct 接口（Entity ↔ DTO/VO）
- type/   领域枚举（与 PG enum 对应）
- security/ JWT、AES-GCM 加密
- config/   SecurityConfig、Neo4jConfig
```

✅ 包根：`com.ai_kids_care.v1`。`v1` 与 API 路径 `/api/v1` 对应，🔶 推断为预留 API 版本化空间。

### 设计契约

| 约定 | 说明 | 证据 |
| --- | --- | --- |
| 输入用 DTO，输出用 VO | Controller 不直接暴露 Entity | 所有 controller 签名 |
| Controller 不含业务逻辑 | 仅委托 service | 如 `ChildrenController.java` |
| 构造器注入 | `@RequiredArgsConstructor` + `final` 字段 | 全体 service/controller |
| 分页 | Spring `Pageable` + `@PageableDefault(size=20)` | `ChildrenController.listChildren` |
| OpenAPI 标注 | `@Tag` 等 springdoc 注解 | controller 顶部 |

## 3. 代码生成器（关键架构事实）

✅ **后端 CRUD 各层的高度同构，源于一个代码生成器**：`pg-spring-crud-codegen/`（Python；原 `scripts/codegen/`，2026-05-29 迁址，见 [ADR-0011](../decisions/adr/ADR-0011-extract-codegen-subproject.md)）。

`pg-spring-crud-codegen/main.py` 的流程：

```text
PostgreSQL schema
   │ introspect_pg.py（读 information_schema：表/列/主键/外键/注释）
   ▼
EntityModel / FieldModel（model.py）+ 命名转换（naming.py）+ PG→Java 类型映射（type_map.py）
   │ pystache 渲染
   ▼
templates/*.mustache  →  生成 6 类文件：
   CreateDTO / UpdateDTO / Mapper / VO / Controller / Service
```

含义（对维护者很重要）：

- 后端实体层是**数据库优先（DB-first）**：先有 PG schema，代码由其生成。
- 新增一张表后，可用生成器快速产出 CRUD 骨架，再手工补业务逻辑。
- 🔶 生成器是**一次性脚手架**：生成后代码已手工演进（如 `AuthService` 的复杂注册逻辑、`GraphRepository`），不存在"重新生成会覆盖"的双向绑定。
- 详见 [engineering/backend-guide.md](../engineering/backend-guide.md)。

## 4. 持久化配置

✅ `application.yml`：

- `spring.jpa.hibernate.ddl-auto: validate` — **Hibernate 不建表**，仅校验实体与现有表是否匹配。表必须由 `db/initdb/*.sql` 预先建好。
- `open-in-view: false` — 关闭 OSIV，懒加载须在事务内完成（良好实践）。
- `show-sql: false`，方言 `PostgreSQLDialect`。
- 数据源、Neo4j、JWT secret 全部通过环境变量注入，带本地默认值。

> ⚠️ `logging.level.root: DEBUG` ✅——committed 配置为 DEBUG 级，生产环境会产生大量日志且可能泄露敏感信息。见 [open-questions](../modernization/open-questions.md)。

## 5. 认证与安全（概要）

✅ 组件：

- `JwtUtil` — HMAC-SHA 签名，`subject` = 登录标识符，24h 过期。
- `JwtAuthenticationFilter` — 从 `Authorization: Bearer` 解析并校验。
- `AesGcmCryptoUtil` — AES-256-GCM（32B 密钥/12B IV/128b tag），用于摄像头流凭证。
- `BCryptPasswordEncoder` — 用户密码。

> ❓ **关键现状**：`SecurityConfig` 中 `JwtAuthenticationFilter` 的注册被**注释掉**，且 `/api/v1/**` 为 `permitAll()`——**鉴权实际未启用**。完整分析见 [security-architecture](security-architecture.md)。

## 6. 双数据源

✅ 后端同时连两个库：

- **PostgreSQL**：Spring Data JPA，承载全部业务实体。
- **Neo4j**：`Neo4jConfig` 装配官方 `Driver`，`GraphRepository` 用**原生 Cypher**（非 OGM/Spring Data Neo4j）执行只读图查询。

## 7. 已知缺口

| 缺口 | 状态 |
| --- | --- |
| 测试覆盖薄 | ✅ 已有 Testcontainers 后端基线，但仅覆盖认证与检测事件少量路径；前端/AI 无测试 |
| 无全局异常处理器 | 🔶 未见 `@ControllerAdvice`；service 多直接抛 `RuntimeException`/`IllegalArgumentException`，错误响应格式未统一 |
| 鉴权关闭 | ✅ 见上 |
| 密码重置未实现 | ✅ `AuthService.passwordResets` 抛 `Not implemented` |

详见 [modernization/current-state-assessment.md](../modernization/current-state-assessment.md)。
