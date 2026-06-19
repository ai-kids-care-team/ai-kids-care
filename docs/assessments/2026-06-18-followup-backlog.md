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

## Wave 2（已完成 — 2026-06-18）

| ID | 描述 | 状态 |
| --- | --- | --- |
| BE-2 | 休眠 Service（Graph/DetectionEvent/EventReview）补 `@PreAuthorize("denyAll()")` | Done（ee29f30） |
| BE-3 | VO 收窄：ChildGraphVO 去 staffNo/address（BE-3a）+ AnnouncementVO 去 authorId（BE-3b） | Done（ee29f30 / 17fb19b）；前端类型清理见 FE-authorId |
| AI-1 | `/predict/upload` 同步推理改 `run_in_threadpool` | Done（ee29f30） |
| AI-3 | 推理 Form 参数范围校验（top_k/num_frames/sampling_rate → 422） | Done（ee29f30） |
| CODEGEN | 移除 `pg-spring-crud-codegen` + ADR-0027 + 清引用 | Done（123c57e；ADR-0027 Proposed 待 Accept） |
| DB-2a | announcements 补 kindergarten_id | **关闭：误读**。announcements 经维护者确认为**平台级**（superadmin 作者面向所有园，与 seed/标题一致）；加 kindergarten_id 方向错误，且回填不可行（admin user_id=1 无 ACTIVE membership）。→ 衍生 AN-READ |
| DB-2b | ClassRoomAssignment 补 kindergarten 实体映射 | **关闭：不做**。全仓 assignment 一致用单列 join、复合 FK 已在库层保证；改它引入新模式+不一致，无具体需求 |

### AN-READ（DB-2 衍生，P1 真实 bug）

平台级语义暴露出当前 read 模型缺陷：`AnnouncementRepository` 按「作者 membership」EXISTS 过滤，而平台公告作者（superadmin）无 membership → **这些公告对所有人不可见**。
修复方向：announcements 改平台级 read——所有认证用户可见 ACTIVE、未删除公告；移除 author-membership 过滤；相应调整 `@PreAuthorize`（tenant-scoped → 任一认证用户）与契约/授权测试。
状态：**Done**（commit 83b1862）。新增 AuthorizationAction PLATFORM_ANNOUNCEMENT_READ（任一认证用户）/PLATFORM_ANNOUNCEMENT_WRITE（PLATFORM_IT_ADMIN）；移除 TENANT_ANNOUNCEMENT_*；AnnouncementRepository 去 author-membership 过滤改平台广读；写收到 PLATFORM_IT_ADMIN（杜绝跨园广播）。AnnouncementAuthorizationIntegrationTest 11 通过（跨园可读、租户管理员写 403、PLATFORM_IT_ADMIN 写 201、未认证 401）+ 契约/授权回归过。

---

## Wave 3（ADR 链）

| ID | 描述 | 状态 |
| --- | --- | --- |
| SEC-D7 | RRN 再校验/回填流程 | **关闭**：「BCrypt 回填」语义已 OBE（V5/V6 已删 rrn_encrypted、无存量）；剩余「持有明文时机重算 hash」价值并入 ADR-0025 R4（2026-06-18 SEC-D7 设计评估） |
| SEC-0025 | pepper 轮换实现 | **推迟（build-on-demand）**：设计完整（ADR-0025 R1–R5 + 2026-06-18 设计确认）；break-glass 能力、生产 pepper 已强、无轮换在即；实施前须定 OQ-1~4（强制 vs 懒 / 休眠账号 / 端点认证 / 过渡期），见 ADR-0025 状态注 |
| SEC-0026 | 摄像头流密码加密链路（已批准）：Java 加密写 + Python 解密读 + 密钥下发两端 + 真实种子加密 | **进行中**（Wave 3 当前聚焦） |

---

## Wave 4（前端 + 测试 + CI 收尾）

| ID | 描述 | 前置 | 优先级 |
| --- | --- | --- | --- |
| BE-4 | `/auth/session` 返回实名 `name` | **Done**（当前 worktree）。按角色从 teachers/guardians/superadmins 档案延迟查 name，PLATFORM_IT_ADMIN 返回 null（NON_NULL 抑制 key）；AuthSessionVO + EffectiveAuthorizationContext 添加 name 字段；GuardianRepository 添加 findByUser_Id；AuthEndpointTest 扩展 superadmin/teacher name 断言 + null-name 覆盖；PublishedOpenApiContractTest 更新 AuthSessionVO 属性集。 | P2 |
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
