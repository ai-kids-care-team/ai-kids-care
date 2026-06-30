# AI Kids Care

[한국어](README.md) | [English](README.en.md) | **中文**

AI Kids Care 是面向幼儿园场景的 AI 安全管理平台。它把 CCTV 摄像头、监护人、教师、儿童、通知、公告、感谢信，以及 AI 识别出的安全事件连接成一个完整的业务流程。本仓库是一个 monorepo，包含前端、后端、数据库资产、AI 推理服务和部署自动化配置。

## 项目概览

系统的目标是帮助幼儿园运营人员管理摄像头和班级/教室数据，审核 AI 检测到的事件，并把必要信息传达给监护人和教职员。主要流程包括：

- 注册、登录、服务端会话认证（Spring Session + Redis + cookie + CSRF）和基于角色的菜单
- 幼儿园、班级、教室、儿童、监护人、教师数据管理
- CCTV 摄像头和视频流管理
- AI 检测会话、检测事件、证据文件和事件审核
- 公告和感谢信
- 基于 Neo4j 的儿童关系图查询
- Pushover 和 SMS 实时告警实验

## 核心功能

| 领域 | 内容 |
| --- | --- |
| 认证与权限 | `GUARDIAN`, `TEACHER`, `KINDERGARTEN_ADMIN`, `PLATFORM_IT_ADMIN`, `SUPERADMIN` 角色，服务端会话认证（Spring Session + Redis + cookie + CSRF）登录/刷新/登出，按角色展示菜单（ADR-0016） |
| 幼儿园运营数据 | 幼儿园、班级、教室、教师、监护人、儿童、班级分配、监护关系、教室分配 |
| CCTV 与事件 | 摄像头、视频流、AI 模型、检测会话、检测事件、审核、证据文件 |
| 沟通功能 | 公告、感谢信、通知规则、设备 token、通知历史 |
| 图数据 | 使用 Neo4j 构建以儿童为中心的关系图 |
| AI 推理 | 基于 VideoMAE 的视频分类，按路径/上传文件预测 API，实时流检测与告警实验 |

## 技术架构

| 层级 | 技术 |
| --- | --- |
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS, Radix UI, Redux Toolkit, Axios |
| Backend | Java 21, Spring Boot 3.2.5, Spring Web, Spring Security, Spring Data JPA, Validation, MapStruct, Springdoc OpenAPI, Neo4j Java Driver |
| Database | PostgreSQL 16, Neo4j 5.19, SQL 初始化脚本、种子数据、DBML、ERD 图 |
| AI | Python, FastAPI, Uvicorn, PyTorch, Transformers VideoMAE, AV/FFmpeg, Pushover, SMS |
| DevOps | Docker, Docker Compose, Nginx, Gradle, Jenkinsfile |

## 目录结构

```text
.
|-- frontend/             # Next.js UI, pages, components, Redux store, API clients
|-- backend/              # Spring Boot API server
|-- ai/                   # VideoMAE training, inference, serving, stream alert scripts
|-- db/                   # PostgreSQL schema, seed data, Neo4j loader, DB utilities
|-- openspec/             # OpenSpec specs (openspec/specs) and change proposals
|-- （pg-spring-crud-codegen/ 已退役，2026-06-18，ADR-0027）
|-- jenkins/              # Jenkins image and compose helper
|-- docker-compose.yml    # Main stack: PostgreSQL, Neo4j, data loader, backend, frontend
|-- Jenkinsfile           # CI/CD pipeline for compose deployment
`-- README*.md            # Multilingual project documentation
```

## 快速启动

根目录的 `docker-compose.yml` 会同时启动 PostgreSQL、Neo4j、Neo4j 数据加载器、Spring Boot 后端，以及基于 Nginx 的前端。

```bash
docker compose up -d --build
```

启动后的主要访问地址：

| 服务 | 地址 |
| --- | --- |
| Frontend | `http://localhost` |
| Backend API | `http://localhost:8080/api/v1` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| Neo4j Browser | `http://localhost:7474` |
| PostgreSQL | `localhost:5432` |

`docker-compose.yml` 已为本地开发提供 fallback 环境变量。生产或多人共享环境中，请参考根目录 `.env.example` 创建 `.env`，并显式设置数据库账号、Neo4j 账号和 JWT secret。

```bash
cp .env.example .env
docker compose up -d --build
```

## 本地开发

可以只用 Docker 启动数据服务，然后在本地分别运行后端和前端进程。

```bash
docker compose up -d db neo4j data-loader
```

后端：

```bash
cd backend
./gradlew bootRun
```

Windows PowerShell：

```powershell
cd backend
.\gradlew.bat bootRun
```

前端：

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器默认运行在 `http://localhost:3000`。API 默认地址是 `http://localhost:8080/api/v1`，如需覆盖可参考 `frontend/.env.example` 设置 `NEXT_PUBLIC_API_BASE_URL`。

常用开发命令：

```bash
# frontend
npm run lint
npm run build

# backend
./gradlew test
./gradlew bootJar
```

## AI 服务

AI 模块可以独立于根 compose 栈运行。推理 API 使用 FastAPI 提供服务，端口为 `8001`。

```bash
cd ai
docker compose up -d --build
```

本地 Python 运行：

```bash
cd ai
python -m venv .venv
source .venv/bin/activate
pip install uv
uv sync --no-dev
export PYTHONPATH=src
python scripts/serve.py
```

Windows PowerShell：

```powershell
cd ai
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install uv
uv sync --no-dev
$env:PYTHONPATH = "src"
python scripts\serve.py
```

AI 服务主要端点：

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/health` | 查看模型路径、设备和标签状态 |
| `POST` | `/predict/path` | 使用服务端可访问的视频文件路径进行预测 |
| `POST` | `/predict/upload` | 使用上传的视频文件进行预测 |

`AI_MODEL_DIR` 默认值为 `outputs/videomae_baseline/best_model`。无论 Docker 还是本地运行，该路径都需要有真实模型文件才能进行推理。实时流推理与告警实验主要围绕 `ai/scripts/stream_live_alert_service.py` 构建。

## 数据库与文档

- PostgreSQL schema 和 seed SQL：`db/initdb/`
- DBML schema：`db/dbml/schema.dbml`
- ERD 文档：`openspec/specs/data-platform/spec.md`
- Neo4j 数据加载器：`db/ne4j_kindergartens/`（直接查询 PostgreSQL 一次性重建派生图；不使用 CSV）
- 代码生成工具：~~`pg-spring-crud-codegen/`~~（已退役，2026-06-18，ADR-0027；新增领域对象改为手写）

后端以 Hibernate `ddl-auto=validate` 模式运行，因此应用启动前数据库 schema 必须已经存在。使用根目录 Docker Compose 时，PostgreSQL 容器创建阶段会自动应用 `db/initdb`。

## 主要 API 区域

后端 API 挂载在 `/api/v1` 下。

- 认证：`/auth/login`, `/auth/logout`, `/auth/refresh`, `/auth/register`, `/auth/password-resets`
- 运营数据：`/users`, `/kindergartens`, `/classes`, `/rooms`, `/children`, `/teachers`, `/guardians`
- CCTV 与 AI 事件：`/cctv_cameras`, `/camera_streams`, `/ai_models`, `/detection_sessions`, `/detection_events`, `/event_reviews`, `/event_evidence_files`
- 沟通：`/announcements`, `/appreciation_letters`, `/notifications`, `/notification_rules`, `/device_tokens`
- 图查询：`/graph/children/{childId}`
- 通用代码与菜单：`/common_codes`, `/menus`

启动后端后，可以通过 Swagger UI 查看详细请求和响应 schema。

## 后续开发方式说明

从 `2026-05-11` 起，本项目的后续开发将大量使用 Vibe Coding 与 AI Agents。做出这一选择主要有两个原因：一是 Vibe Coding 技术迅速发展，已经能够显著提升单人开发和维护效率；二是项目原本由 3 人协作开发，后续实际维护/开发人力减少为 1 人，因此使用 AI Agents 成为必要选择。

需要说明的是，`2026-05-11` 之前的提交、功能和贡献统计仍可理解为传统团队开发模式下形成的成果；本说明仅用于区分未来开发方式，避免将整个项目误解为从一开始就完全由 AI Agents 生成。

## 贡献度与职责

以下统计只包含 `2026-04-10 00:00:00 +0900` 之前的提交。该 cutoff 下最后纳入统计的提交是 `0c6dda6`（`2026-04-08 22:14:27 +0900`）。之后的提交，以及本次 README 重写，不纳入统计。

统计口径：

- Commit 占比：基于 cutoff 前共 407 次提交
- Churn 占比：基于 `git numstat` 的 added + deleted 行数，总计 256,644 行
- 职责：根据 cutoff 前提交信息和目录级改动分布推断

| Contributor | Commits | Commit share | Churn | Churn share | 主要职责 |
| --- | ---: | ---: | ---: | ---: | --- |
| Zhang Junfan 장준범 | 323 | 79.4% | 186,294 | 72.6% | 项目负责人、架构师、主程。主导后端 API、数据模型、AI 训练/推理流水线、实时告警，以及 Docker/Jenkins 配置。 |
| korea4050-debug | 63 | 15.5% | 29,716 | 11.6% | 负责后端与前端集成、DB/Neo4j 设置、种子数据，以及公告、认证、异常检测流程的补充完善。 |
| deokwoo-han | 21 | 5.2% | 40,634 | 15.8% | 前端工程师。重点负责 CCTV 监控仪表盘、感谢信页面、前端页面修复，以及少量后端联动补充。 |
