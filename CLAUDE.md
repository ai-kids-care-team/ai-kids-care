# AI Kids Care

**多租户幼儿园 AI 安全看护平台**（长期生产系统，monorepo）。核心价值是一条告警闭环：
**CCTV → AI（VideoMAE）实时检测异常行为 → 教职员复核 → 通知家长**。附带教职员/家长沟通工具（公告、感谢信、通知收件箱）、园所运营数据管理、儿童关系图查询。

> **闭环状态**：ADR-0015 V1 链路（AI 检测 → `POST /api/v1/internal/detection-{sessions,events}` → SSE + 通知，backend 为 `detection_events` 唯一写入者）**两侧已实现**。AI 侧 supervisor 运行细节与 compose gating 见 `.claude/rules/ai.md`。

---

## 工作范式

- **「做什么」用 OpenSpec**：能力沉淀在 `openspec/specs/`；变更走 `propose → apply → archive`。项目上下文见 `openspec/config.yaml`。起步：`/opsx:propose "<idea>"`；实现：`/opsx:apply`。
- **「怎么可靠地做」用 superpowers 技能**：brainstorming / writing-plans / executing-plans / test-driven-development / verification-before-completion / requesting-code-review / using-git-worktrees / finishing-a-development-branch 等。
- **优先级**：用户显式指令 > superpowers 技能 > 默认行为。
- **破坏性任务**（删除 / 迁移 / schema / 部署）须在 apply 前经维护者**逐个批准**。提案须写明 Why 与 Non-goals。

---

## 技术栈

| 组件 | 技术 | 关键版本 |
|------|------|----------|
| `backend/` | Spring Boot REST API + Spring Security + Spring Session(Redis) + Data JPA/Hibernate + Flyway + MapStruct + Neo4j Java Driver | Java 21, Spring Boot 3.2.5, Gradle 8.7 |
| `frontend/` | Next.js（`output: 'export'` 纯静态导出，无 SSR）+ React + Redux Toolkit(RTK Query) + Axios + Tailwind | Next 16.1.6, React 19.2.3, Tailwind 4.2.1, TS 5 |
| `ai/` | FastAPI 推理服务（端口 8001）+ VideoMAE（HuggingFace Transformers）+ PyTorch + PyAV；独立 compose 栈 | Python ≥3.12, FastAPI 0.115.12 |
| `db/` | PostgreSQL（system-of-record）+ Neo4j（只读派生关系图）+ Redis（会话/限流） | Neo4j 5.19, Redis 7 |
| `infra/` | Caddy 边缘反代（生产 ACME TLS 终止） | Caddy 2 |
| `e2e/` | Playwright（发版门禁） | — |

包管理：backend=Gradle、frontend=npm、ai=uv、e2e=npm。

> **写代码前必读对应组件规则**：各组件的实现约定与命令已下沉到 `.claude/rules/`（按 path 渐进式披露，见下方「rules 索引」）。

---

## 构建 / 测试 / 运行

```bash
# 全栈本地（含 initdb 种子）
docker compose up -d --build

# E2E：仅 release.yml 中运行
cd e2e && npx playwright test
```

各组件的构建/测试命令见对应 rule（`backend.md` / `frontend.md` / `ai.md` / `db.md`）。

- **必填环境变量**（无弱默认，缺失则 compose 报错，从 `.env.example` 复制）：`POSTGRES_PASSWORD`、`NEO4J_PASSWORD`、`REDIS_PASSWORD`、`RRN_HASH_PEPPER`、`CAMERA_STREAM_AES_KEY_V1`（32 字节 Base64）、`AI_SERVICE_TOKEN`。
- **演示登录**：所有 demo 账号统一密码 `admin123`（来源 `db/initdb/` seed，见 `docs/demo-accounts.md`）。
- **时区**：全部服务 `TZ=Asia/Seoul`，JVM 加 `-Duser.timezone=Asia/Seoul`。
- **Claude hook 解释器仅 git/powershell**（bash=WSL；node 是否在 hook 执行 PATH 未确证，hook 内保守勿依赖）。

---

## CI / 分支 / 发布 / CD

**CI（`.github/workflows/`）**：单一 `ci.yml`（`name: CI`，4 并行 job：`Backend (Gradle, Java 21)` / `Frontend (lint & build)` / `AI (pytest, Python 3.12)` / `Compose config`；develop/main push + PR + workflow_dispatch 触发）；`release.yml` 独立，构建 4 镜像 → `docker compose` smoke → 等 `/api/v1/auth/csrf` 200 → **Playwright E2E 硬门禁** → 推 `:version`（`:prod` 需 GitHub Environment 人工审批）。**后端 Gradle test 不在 release 链路**。

- `develop` = 集成 trunk（直接提交）；`main` = 受保护发布线，经 `develop → main` PR。
- 发布门：GitHub Actions（Compose Config）+ code-owner 批准 + 全新独立评审 + 维护者 merge。
- 发布后 `release.yml` 构建 + 冒烟 + E2E +（production 环境批准后）推 `:prod`，远程 **watchtower** 轮询部署。
- Compose 分层：`docker-compose.yml`（基础/含种子）+ `.prod.yml`（Caddy TLS、Dockerfile.prod 无种子、Secure cookie）+ `.cd.yml`（GHCR 镜像 + watchtower）。

---

## 安全红线（5 条摘要）

1. 会话式认证（Spring Session + Redis），**无 JWT**；每请求重解析授权。
2. **CSRF 强制**所有写请求；唯一豁免 `/api/v1/internal/**`（Bearer）。
3. **default-deny**：`anyRequest().authenticated()` 兜底，新端点自动受保护。
4. **RRN 单向哈希**（HMAC-SHA256+pepper，`rrn_hash`） vs **摄像头凭据 AES-256-GCM 可逆**，两机制不可混用；RRN 不落明文/日志。
5. **密钥全 `${ENV}` 注入 + fail-fast**；secret/PII 绝不入日志。

>（完整 rationale + 多租户隔离全文 + 「测试占位/demo≠漏洞」「冷启动管理员/限流是受控机制」避免误报清单见 `.claude/rules/security.md`。）

---

## rules 索引（`.claude/rules/`）

写代码前，对应目录的实现约定会由 `.claude/hooks/disclose-path-rules.ps1` **按 path 命中、每 session 去重后渐进式披露**。清单：

| rule | glob | 职责 |
|---|---|---|
| `backend.md` | `backend/**` | 分层与命名、`@PreAuthorize`、JPA、Spring 事件、`@Async`/事务外 IO、SSE 服务端实现、gradle 测试 + DooD |
| `frontend.md` | `frontend/**` | App Router、`services/apis/` 双客户端 + CSRF 回填、绝不传 kindergartenId、静态导出、npm 命令、eslint hook、gitignore 陷阱 |
| `ai.md` | `ai/**` | FastAPI :8001、supervisor process-per-stream + `GET /internal/streams` reconcile + compose gating、uv/pytest、ingest 客户端重试 |
| `db.md` | `db/**` | 存储三分、Flyway 单一 V1 baseline、DBML 真源 + baseline-on-migrate、Neo4j 只读派生无 PII、seed=fixture→cleanTest |
| `contracts.md` | `backend/**, frontend/**, ai/**` | REST 基线、ingest 契约、SSE 线协议、enum 单一真源、内部事件链、通知渠道、REST 命名不统一、Caddy SSE |
| `security.md` | `backend/**, frontend/**` | 多租户隔离全文 + 安全 invariants 1-7 完整 rationale + 避免误报清单 |

---

## 语言策略

- 用请求所用语言与维护者沟通；持久工程文档**简体中文为主、英文术语为辅**。
- 保留代码标识符、API 路径、enum 值、数据库名、韩语产品文案不变。

---

> 组件多角度分析、代码审查等能力由对应 skill 按需自触发，不在本文件登记。组件级实现约定见 `.claude/rules/`（按 path 渐进式披露层）。harness 自身的设计/变更历史见 OpenSpec change 与各 skill 文件。
