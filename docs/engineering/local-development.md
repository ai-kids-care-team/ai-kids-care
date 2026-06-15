# 本地开发（Local Development）

✅ 来源：根 `README.md`、`docker-compose.yml`、各组件 `Dockerfile` 与 `README`。命令以仓库文档为准。

## 前置条件

- Docker + Docker Compose
- 后端本地运行：JDK 21
- 前端本地运行：Node.js（Dockerfile 用 node:20）
- AI 本地运行：Python（README 称 3.14 / CUDA 13.2；Docker 用 3.12）+ FFmpeg

## 方式一：整栈 Docker（最快）

✅ 根 `docker-compose.yml` 一键启动 PostgreSQL、Neo4j、data-loader、后端、前端（**不含 AI**）：

```bash
docker compose up -d --build
```

启动后入口：

| 服务 | 地址 |
| --- | --- |
| 前端 | http://localhost |
| 后端 API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Neo4j Browser | http://localhost:7474 |
| PostgreSQL | localhost:5432 |

✅ 环境变量在 compose 中有 fallback 默认值。生产/共享环境应基于根 `.env.example` 创建 `.env` 显式设置 DB 账户、Neo4j 账户、JWT secret：

```bash
cp .env.example .env
docker compose up -d --build
```

## 方式二：数据库用 Docker，后端/前端本地跑（便于调试）

```bash
# 仅起数据层
docker compose up -d db neo4j data-loader
```

后端：

```bash
cd backend
./gradlew bootRun          # Linux/Mac
.\gradlew.bat bootRun      # Windows PowerShell
```

前端：

```bash
cd frontend
npm install                # 如遇 peer 冲突：npm install --legacy-peer-deps
npm run dev                # 默认 http://localhost:3000
```

✅ 前端 API 默认指向 `http://localhost:8080/api/v1`，可用 `NEXT_PUBLIC_API_BASE_URL`（参考 `frontend/.env.example`）覆盖。

## 方式三：AI 服务（独立）

✅ AI 不在根 compose 中，需单独启动（`ai/docker-compose.yml`，端口 8001）：

```bash
cd ai
docker compose up -d --build
```

本地 Python 运行：

```bash
cd ai
python -m venv .venv
source .venv/bin/activate            # Windows: .\.venv\Scripts\Activate.ps1
pip install --extra-index-url https://download.pytorch.org/whl/cu130 -r requirements.txt
export PYTHONPATH=src                 # Windows: $env:PYTHONPATH="src"
python scripts/serve.py
```

> ⚠️ 推理需要模型权重存在于 `ai/outputs/videomae_baseline/best_model`（不在仓库中，需外部提供）。否则服务启动加载模型会失败。

AI 端点：`GET /health`、`POST /predict/path`、`POST /predict/upload`（见 [api/ai-service-api.md](../api/ai-service-api.md)）。

## 常用开发命令

```bash
# 前端
npm run lint
npm run build

# 后端
./gradlew test     # ⚠️ 当前无测试用例（见 testing.md）
./gradlew bootJar
```

## 重要前提

- ✅ 后端 `ddl-auto=validate`：**启动前 DB schema 必须已存在**。用根 compose 时，PostgreSQL 容器首次创建会自动执行 `db/initdb/*.sql`。
- ✅ Neo4j 图数据由 data-loader 一次性从 PostgreSQL 加载（`restart: no`）。
- ✅ 全栈时区固定 Asia/Seoul。

## 排障

常见问题与处理见 [operations/runbook.md](../operations/runbook.md)。
