# 后端开发指南（Backend Guide）

✅ 来源：`backend/`、`pg-spring-crud-codegen/`（原 `scripts/codegen/`，2026-05-29 迁址，见 [ADR-0011](../decisions/adr/ADR-0011-extract-codegen-subproject.md)）。架构总览见 [architecture/backend-architecture.md](../architecture/backend-architecture.md)。

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

## 代码生成器：从一张表生成 CRUD 骨架

✅ `pg-spring-crud-codegen/`（Python + psycopg + pystache）。流程见 [ADR-0004](../decisions/adr/ADR-0004-layered-backend-codegen.md) 与 [ADR-0011](../decisions/adr/ADR-0011-extract-codegen-subproject.md)。

它内省 PostgreSQL，按 `templates/*.mustache` 为每张表生成 6 类文件：`CreateDTO`、`UpdateDTO`、`Mapper`、`VO`、`Controller`、`Service`。

✅ 通过环境变量配置（`pg-spring-crud-codegen/main.py`）：

| 变量 | 含义 | 示例 |
| --- | --- | --- |
| `PG_DSN` | PostgreSQL 连接串 | `postgresql://kids_user:kids_pass@localhost:5432/kids_postgres_db` |
| `PG_SCHEMA` | schema | `public` |
| `JAVA_PACKAGE_BASE` | 生成代码的包名 | `com.ai_kids_care.v1` |
| `OUT_JAVA` | 输出根目录 | `./out/src/main/java` |
| `ONLY_TABLES` | 仅生成指定表（逗号分隔，可选） | `children,classes` |
| `EXCLUDE_TABLES` | 排除表（可选） | `audit_logs` |

运行（🔶 推断的典型用法）：

```bash
cd pg-spring-crud-codegen
cp .env.example .env      # 填入上述变量
pip install -r requirements.txt
python main.py            # 生成到 OUT_JAVA
```

> 🔶 codegen 是**一次性脚手架**：生成后请把文件挪入 `backend/` 并**手工补业务逻辑**。它与既有代码无双向绑定，重新生成不会、也不应直接覆盖已演进的代码。校验生成结果后再合并。

## 新增一个领域对象的典型步骤（🔶 推断的推荐路径）

1. 在 `db/dbml/schema.dbml` 增表 → 生成 SQL（见 [database-guide](database-guide.md)）。
2. （可选）用 codegen 生成 CRUD 骨架。
3. 在 `entity/` 写/校对 JPA 实体（必须与表结构一致，否则 `ddl-auto=validate` 启动失败）。
4. 补 `repository`/`service` 业务逻辑、`mapper` 映射、`dto`/`vo`。
5. Controller 暴露 `/api/v1/...`。
6. 更新 [api/rest-endpoints.md](../api/rest-endpoints.md) 与相关文档。
7. （应当）补测试——但当前项目无测试基线，见 [testing](testing.md)。

## 访问 Neo4j

✅ 图查询不用 Spring Data Neo4j，而是 `Neo4jConfig` 装配官方 `Driver`，在 `GraphRepository` 写**原生 Cypher**。参考 `findChildGraph` 的写法（`executeRead` + 手工 `Record→VO` 映射）。

## 注意事项 / 陷阱

- ⚠️ **鉴权已启用**（默认拒绝 + 服务端会话，[security-architecture](../architecture/security-architecture.md)）：调 `/api/v1/**` 需先 `/auth/login` 拿会话 cookie（`AI_KIDS_CARE_SESSION`）+ CSRF（`GET /auth/csrf` → `X-XSRF-TOKEN` 头）。后端测试需 Docker（Testcontainers：Postgres + Redis）。
- ⚠️ `logging.level.root: DEBUG`：调试方便但日志量大，注意敏感信息。
- ⚠️ 无统一异常处理器：service 抛 `RuntimeException`/`IllegalArgumentException`，错误响应格式不统一。
- ⚠️ 无测试基线：改动后请手工验证关键流程。
