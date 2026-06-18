---
type: assessment-backlog
date: 2026-06-18
status: Active
based_on: 2026-06-17-followup-audit.md
---

# AI Kids Care 2026-06-18 修复 Backlog

本文件记录 2026-06-17 审计后的待处理工作项，按 Wave 分层排列。Wave 0 为可立即执行项；Wave 1–4 为依赖前置完成的后续项。

HALT 项须人工决策后方可继续。

---

## Wave 0（已全部完成 — 2026-06-18）

| ID | 描述 | 状态 |
| --- | --- | --- |
| SEC-SEED（工作区） | 删除 `db/initdb/26_children_seed.sql` 明文 RRN 注释 | Done |
| SEC-SEED（history） | `git-filter-repo` 抹除全历史 15 个 RRN 串，force-push `develop`(7d3307d)+`main`(886ce85)；独立 clone 验证两分支历史无 RRN；seed dummy data 零丢失（仅 2 个测试文件的 Javadoc 注释被 redact，不影响测试） | Done |
| AI-4 | `pushover.py` priority 参数化（默认 High=1；仅 Emergency≥2 带 retry/expire；支持 `PUSHOVER_PRIORITY`） | Done |
| AI-2 | `ai/` 迁移至 uv（新增 `pyproject.toml`、删 `requirements*.txt`、Dockerfile/README/test 更新；torch 经 cu130 index 对应 CUDA 13.2） | Done（`uv lock` 待 deploy host 跑一次验证/锁版本） |
| CODEOWNERS | 删除（唯一维护者、无 coworker，避免 PR codeowner 飘红） | Done |
| CI-1 | `production` 环境 required reviewer = 维护者（SimpleJerry），`prevent_self_review=false` → 每次 `deploy-prod` 人工批准（ADR-0022 OQ-3 关闭）；实测原已配置，`release.yml` 过时注释已修正 | Done |
| CI-2 | `main` required status checks 加入 `schema-digest matches migrations`（经 gh api，现共 4 项） | Done |
| DOC-audit | `2026-06-10-codebase-audit.md` 追加废弃提示 | Done |
| DOC-adr | `ADR-0020` 追加 required checks 补注 + CODEOWNERS-now-false 修正 | Done |

> 注：SEC-SEED 经维护者澄清为 demo 假数据、非隐私泄露；历史改写已完成即保持不变。`feat/notification-sms-email` 分支与 GitHub `refs/pull/*` 中的旧 RRN 不再处理。**维护者需重新打开历史改写期间临时关闭的 `main`/`develop` force-push 保护。**

---

## Wave 1（已完成 — 2026-06-18）

| ID | 描述 | 状态 |
| --- | --- | --- |
| BE-1 | 全局异常处理（MethodArgumentNotValid→400 不回显 rejectedValue、IllegalArgument→400 固定消息）+ 5 controller `@Valid` + 10 DTO Bean Validation + `application.yml` include-stacktrace:never | Done（commit 3f91add；compile + *ErrorResponse* 金丝雀 + *ContractTest* 过） |
| DB-1 | initdb ↔ Flyway「对齐」 | **关闭：非问题**。bootstrap = initdb V1 基线 + Flyway V2–V6（`baseline-on-migrate`）；生产空库则 Flyway 跑全量 V1–V6。两路径最终 schema 均正确，CI `Schema Digest Drift` 连绿，FlywayMigrationTest/V2SchemaConstraint 在跑。把 V2 `ADD COLUMN`（无 IF NOT EXISTS）塞进 initdb 反会触发双重应用报错。唯一极小技术债：`users.login_id` 冗余 UNIQUE（在 V1 源头、两路径一致，非漂移）→ P3 可不动 |

---

## Wave 2（安全加固，可并行）

各项可独立并行，但 AI 系三项（AI-1/AI-3/SEC-0026）建议一次进入 `ai/` 统一改动。

| ID | 描述 | 前置 | 优先级 |
| --- | --- | --- | --- |
| BE-2 | 休眠 Service 授权 `@PreAuthorize` | 无（独立于 DB-1） | P1 |
| BE-3 | VO 字段收窄（移除密码/RRN hash 等敏感字段） | 软前置 BE-1 | P1 |
| AI-1 | 推理同步阻塞 async 循环修复 | 与 AI-3/SEC-0026 协调同批进入 `ai/` | P2 |
| AI-3 | 推理入参范围校验 | 与 AI-1/SEC-0026 协调同批进入 `ai/` | P2 |
| DB-2 | `Announcement`/`ClassRoomAssignment` 补 `kindergarten_id`（新 migration V7 + initdb 同步） | 无（DB-1 已关闭，DB-2 独立） | P2 |
| CODEGEN | 移除 `pg-spring-crud-codegen`：需 ADR + 删模块 + 清 17 处引用 | 无（独立） | Done（ADR-0027 Proposed；模块已删除；引用已更新） |

---

## Wave 3（ADR 链）

串行依赖，D7 先行。

| ID | 描述 | 前置 | 优先级 |
| --- | --- | --- | --- |
| SEC-D7 | RRN 再校验/回填流程（ADR） | 无 | P1 |
| SEC-0025 | pepper 轮换实现 | SEC-D7 完成后 | P1 |
| SEC-0026 | 摄像头流密码加密链路（已批准）：AI 改动与 Wave 2 协调；DB 改动依赖 DB-1 | SEC-D7/SEC-0025 共享密钥方案；DB-1 | P1 |

---

## Wave 4（前端 + 测试 + CI 收尾）

| ID | 描述 | 前置 | 优先级 |
| --- | --- | --- | --- |
| BE-4 | `/auth/session` 返回实名 `name` | 无 | P2 |
| FE-name | 前端 name 显示修复 | BE-4 先行 | P2 |
| FE-1 | 注册流 CSRF-less fetch 收口 | 无（独立） | P2 |
| FE-2 | 前端不再传服务端身份字段 | 后端感谢信端点就绪 | P2 |
| FE-3 | 死代码清理 | 无（独立，低优先级） | P3 |
| FE-authorId | 前端 announcements TS 类型移除 `authorId`（后端 BE-3b 已从 VO 移除，运行时不受影响，仅类型清理） | 无（独立） | P3 |
| CI-4 | 前端测试 + AI 测试纳入 CI | 依赖 FE/AI 测试先行编写 | P3 |
| CI-3 | 分支保护加固 | CI-1/CI-2 完成后 | P3 |

---

## 残留已知风险

1. **AI-2 / `uv.lock`**：`ai/pyproject.toml` 用 cu130 index 对应 CUDA 13.2（推断），torch/torchvision 具体版本与 `uv.lock` 须在联网 deploy host 执行 `uv lock` 验证并生成；Dockerfile 随后应改为 `uv sync --frozen`。
2. **force-push 保护**：`main`/`develop` 的 force-push 保护在 SEC-SEED 历史改写期间被临时关闭，须由维护者重新开启。
3. **feat 分支 / PR refs 旧 RRN**：`feat/notification-sms-email` 与 `refs/pull/*` 仍含改写前的 RRN（假数据）；经维护者确认非敏感，不再清理。
4. **README Python 版本**：`ai/README.md` 标 Python 3.14、`ai/Dockerfile` 用 3.12；`pyproject.toml` 以 3.12 为准，README 待维护者确认。
