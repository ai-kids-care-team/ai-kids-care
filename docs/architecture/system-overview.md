# 系统总览（System Overview）

## 1. 形态：Polyglot Monorepo

✅ 单仓库内含多个可独立构建的组件，每个组件有独立技术栈、Dockerfile 与（多数）独立 compose 文件。

```text
ai-kids-care/
├── frontend/              # Next.js 16 / React 19（静态导出 + Nginx）
├── backend/               # Spring Boot 3.2.5 / Java 21（REST API）
├── ai/                    # FastAPI + PyTorch VideoMAE（推理与实时告警）
├── db/                    # PostgreSQL 16 + Neo4j 5.19（schema/种子/加载器）
├── docs/                  # 本知识库 + ERD
├── docker-compose.yml     # 整栈编排（db/neo4j/data-loader/backend/frontend）
├── docker-compose.cd.yml  # CD override：拉 GHCR 镜像 + watchtower 自动部署（ADR-0022）
└── .github/workflows/     # GitHub Actions：后端测试 / 前端 lint+build / compose config / release（CD，ADR-0022）
```

> 注：`pg-spring-crud-codegen/` 代码生成器已于 2026-06-18 由 [ADR-0027](../decisions/adr/ADR-0027-retire-pg-spring-crud-codegen.md) 退役删除；`scripts/codegen/` 软指针 stub 同步删除。

代码归属规则（`CODEOWNERS`）已移除——原规则引用的 GitHub 团队（`ai-kids-care-team/*`）均不存在，已于 2026-06-18 删除。

## 2. 技术栈总表

| 层 | 技术 | 证据 |
| --- | --- | --- |
| Frontend | Next.js 16.1.6、React 19.2、TypeScript 5、Tailwind v4、Radix UI、Redux Toolkit、Axios、reagraph、recharts | `frontend/package.json` |
| Backend | Java 21、Spring Boot 3.2.5（Web/Security/Data JPA/Validation）、MapStruct 1.5.5、springdoc-openapi 2.6、jjwt 0.12.3、Neo4j Java Driver 5.19、Pushover client | `backend/build.gradle` |
| Database | PostgreSQL 16、Neo4j 5.19 | `docker-compose.yml`、`db/` |
| AI | Python、FastAPI、Uvicorn、PyTorch、HuggingFace Transformers（VideoMAE）、PyAV/FFmpeg | `ai/pyproject.toml`、`ai/src/ai_app/` |
| DevOps | Docker、Docker Compose、Nginx、Gradle、GitHub Actions（CI + CD）、GHCR 私有镜像、watchtower | 各 `Dockerfile`、`.github/workflows/*`、`docker-compose.cd.yml`（CD，ADR-0022） |

## 3. 运行时拓扑（整栈 docker-compose）

✅ 来源 `docker-compose.yml`。根 compose **不含** AI 服务（AI 有独立的 `ai/docker-compose.yml`）。

```text
                         ┌───────────────────────────────────────┐
   浏览器 :80 ──────────▶│ frontend (Nginx)                       │
                         │  - 静态文件 (Next.js export → /out)     │
                         │  - location /api/ → 反向代理 backend    │
                         └───────────────┬───────────────────────┘
                                         │ /api/ → http://backend:8080/api/
                                         ▼
                         ┌───────────────────────────────────────┐
   :8080 ───────────────▶│ backend (Spring Boot)                  │
                         │  REST /api/v1/**, Swagger UI            │
                         └──────┬──────────────────────┬──────────┘
                                │ JDBC                 │ Bolt
                                ▼                      ▼
              ┌──────────────────────┐   ┌──────────────────────────┐
   :5432 ────▶│ db (PostgreSQL 16)   │   │ neo4j (5.19)              │◀── :7474/:7687
              │  initdb/*.sql 自动执行 │   │  关系图存储                │
              └──────────┬───────────┘   └────────────▲─────────────┘
                         │  读取 PG                     │  写入图
                         └──────────▶ data-loader (Python, 一次性) ┘
```

### 服务与端口

| 服务 | 容器名 | 端口 | 说明 |
| --- | --- | --- | --- |
| frontend | `frontend` | `80:80` | Nginx 托管静态资源 + `/api/` 反代后端 |
| backend | `backend` | `8080:8080` | Spring Boot；`/swagger-ui/index.html` |
| db | `ai-kids-postgres` | `5432:5432` | 启动时执行 `db/initdb/*.sql`；有 healthcheck |
| neo4j | `neo4j` | `7474`(Web) / `7687`(Bolt) | 图数据库 |
| data-loader | （匿名） | — | 一次性任务：从 PG 读数据写入 Neo4j，`restart: no` |
| ai（独立栈） | — | `8001` | FastAPI 推理服务，见 [ai-architecture](ai-architecture.md) |

✅ 全栈统一时区 **Asia/Seoul**（各服务 `TZ` 环境变量 + backend `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Seoul`）。

### 启动依赖

✅ `backend` 与 `data-loader` 依赖 `db`（healthcheck 通过）+ `neo4j`（已启动）；`frontend` 依赖 `backend`。

## 4. 组件协作要点

| 关系 | 机制 | 证据 |
| --- | --- | --- |
| 前端 → 后端 | HTTP REST（生产经 Nginx `/api/` 反代；开发直连 `:8080`） | `frontend/nginx.conf`、`frontend/src/config/api.ts` |
| 后端 → PostgreSQL | Spring Data JPA（`ddl-auto=validate`） | `application.yml` |
| 后端 → Neo4j | Neo4j Java Driver，原生 Cypher | `GraphRepository.java` |
| Neo4j 数据来源 | data-loader 主要读取仓库内 CSV 快照；另有一个 users PG 导入脚本 | `db/ne4j_kindergartens/` |
| 后端 → 外部通知 | Pushover（`PushoverService`） | `build.gradle`、`PushoverService.java` |
| AI 服务 ↔ 其它 | ❓ **基本解耦**：AI 仅对外暴露 FastAPI；不连 DB、不调后端，仅 Pushover/SMS 告警 | `ai/` 全目录无 DB/HTTP-to-backend 代码 |

> 这是理解本系统最重要的一点：**AI 推理与业务后端目前是两套独立运行的子系统**，靠"模型→外部告警"而非"模型→数据库→后端"连接。两者的打通是关键 [open-question](../modernization/open-questions.md)。

## 5. 数据持久化双存储

✅ 详见 [data-architecture](data-architecture.md)：

- **PostgreSQL** — 唯一可信源（system of record）：**30 张业务表**（核心域 28 张来自 `01_create_schema.sql` + 平台字典 `menu`/`common_codes` 2 张来自 02/03 脚本），多租户（`kindergarten_id`），强约束（复合外键、唯一索引）。
- **Neo4j** — 当前是 CSV 快照 + 少量 PG 导入形成的查询视图，尚不能称为 PostgreSQL 的可靠派生视图；且复制了超出图查询需要的敏感字段。
