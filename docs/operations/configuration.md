# 配置（Configuration）

✅ 来源：`docker-compose.yml`、`backend/src/main/resources/application.yml`、`ai/src/ai_app/serving/deps.py`、`ai/Dockerfile`、`frontend/src/config/api.ts`、`.env.example`。

## 环境变量矩阵

### 数据库（PostgreSQL）

| 变量 | 用途 | 默认值（fallback） | 消费方 |
| --- | --- | --- | --- |
| `POSTGRES_DB` / `DB_NAME` | 库名 | `kids_postgres_db` | db、backend、data-loader |
| `POSTGRES_USER` / `DB_USER` | 用户 | `kids_user` | 同上 |
| `POSTGRES_PASSWORD` / `DB_PASSWORD` | 密码 | `kids_pass` ⚠️ | 同上 |
| `DB_HOST` | 主机 | `db`（容器）/ `localhost`（本地） | backend、data-loader |
| `DB_PORT` | 端口 | `5432` | backend、data-loader |

### Neo4j

| 变量 | 用途 | 默认值 | 消费方 |
| --- | --- | --- | --- |
| `NEO4J_URI` | Bolt 地址 | `bolt://neo4j:7687` / `bolt://localhost:7687` | backend、data-loader |
| `NEO4J_USERNAME` | 用户 | `neo4j` | backend、neo4j、data-loader |
| `NEO4J_PASSWORD` | 密码 | `rose100!` ⚠️ | 同上 |

### Flyway（Schema 迁移）

| 配置项 | 值 | 说明 |
| --- | --- | --- |
| `spring.flyway.enabled` | `true` | Flyway 在后端启动时自动运行迁移 |
| `spring.flyway.baseline-on-migrate` | `true` | 检测到有表但无历史表（initdb 场景）时，自动基线化到 V1 并跳过 V1 脚本 |
| `spring.flyway.baseline-version` | `1` | 基线版本号；V1 对应 `V1__initial_baseline.sql` |
| 迁移脚本路径 | `classpath:db/migration/` | Spring Boot 默认路径，V2+ 迁移文件均放此处 |

> **迁移行为矩阵**：
> - 空库（生产首次部署）：`baseline-on-migrate` 不触发，V1 正常执行，建全量 schema。
> - 有表无历史（initdb/demo 场景）：触发基线化，V1 跳过，执行 V2+ 增量迁移。
> - 有表有历史（生产后续部署）：V1 已记录，执行 V2+ 增量迁移。

### 后端（安全 / 运行）

| 变量 | 用途 | 默认值 | 消费方 |
| --- | --- | --- | --- |
| `REDIS_HOST` / `REDIS_PORT` | Spring Session（Redis）会话存储 | `redis` / `6379` | backend |
| `SESSION_COOKIE_SECURE` | 生产会话 cookie `Secure`（仅 HTTPS 下发送） | `false`（**生产须 `true`**） | backend |
| `SESSION_TIMEOUT`（yml） | 会话超时 | `30m` | backend |
| `DOMAIN` / `ACME_EMAIL` | Caddy 边缘 TLS 域名 / ACME 邮箱（生产） | —（**生产须设 `DOMAIN`**） | caddy（prod） |
| `TZ` / `JAVA_TOOL_OPTIONS` | 时区 | `Asia/Seoul` / `-Duser.timezone=Asia/Seoul` | backend |
| `server.port`（yml） | 端口 | `8080` | backend |
| `logging.level.root`（yml） | 日志级别 | `DEBUG` ⚠️ | backend |

### AI 服务

| 变量 | 用途 | 默认值 |
| --- | --- | --- |
| `AI_MODEL_DIR` | 模型目录 | `outputs/videomae_baseline/best_model` |
| `AI_DEVICE` | 设备 | 自动（cuda/cpu） |
| `AI_NUM_FRAMES` | 抽帧数 | `16` |
| `AI_SAMPLING_RATE` | 采样率 | `4` |
| `AI_SERVICE_HOST` / `AI_SERVICE_PORT` | 监听 | `0.0.0.0` / `8001` |
| `PYTHONPATH` | 包根 | `src`（本地）/ `/app/src`（容器） |

> 🔶 实时告警脚本另有 SMS 相关参数（`sms_api_key/secret/sender/recipients`）与 Pushover 配置——具体来源（环境变量/参数）见 `stream_live_alert_service.py` 与 `utils/pushover.py`、`utils/sms.py`（❓ 凭据注入方式未在本库展开）。

### 前端

| 变量 | 用途 | 默认值 |
| --- | --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | 后端 API 基址 | `http://localhost:8080/api/v1`（本地）；Docker 构建时设为 `/api/v1`（经 Nginx 反代） |

## 端口总表

| 端口 | 服务 |
| --- | --- |
| 80 | frontend（Nginx） |
| 3000 | 前端开发服务器（`npm run dev`） |
| 8080 | backend |
| 8001 | AI 推理服务 |
| 5432 | PostgreSQL |
| 7474 / 7687 | Neo4j Web / Bolt |
| 6379 | Redis（Spring Session 会话存储） |
| 443 | 生产 Caddy 边缘 TLS（HTTPS；80→443 强制重定向，ADR-0017） |

## 配置加载机制

- ✅ 后端：`application.yml` 用 `${ENV:default}` 占位，值来自环境变量，缺省回退默认值。
- ✅ AI：`serving/deps.py` 的 `get_settings()` 从 `os.getenv` 读取，`@lru_cache` 缓存。
- ✅ 前端：`config/api.ts` 读取 `process.env.NEXT_PUBLIC_API_BASE_URL` 并做大小写规范化。

## ⚠️ 配置相关风险（已确认，事实陈述）

- 所有数据库/Neo4j 密钥都有**硬编码明文默认值**，便于开发但生产必须覆盖（原 `JWT_SECRET` 已随 ADR-0016 移除，JWT→服务端会话）。生产另须设 `SESSION_COOKIE_SECURE=true` 与 Caddy 的 `DOMAIN`。
- 根目录存在 `.env`（89 字节）——其内容是否含真实密钥、是否被 git 忽略，见 [open-questions](../modernization/open-questions.md)（OQ-SEC-5）。
- `.gitignore` 体积较大（5KB+），具体忽略项请核对。
