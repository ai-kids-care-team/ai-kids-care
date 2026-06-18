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

## Wave 0（立即可执行 + HALT 待人工）

### 已执行（2026-06-18）

| ID | 描述 | 状态 |
| --- | --- | --- |
| SEC-SEED（工作区） | 删除 `db/initdb/26_children_seed.sql` 第 1–2 行明文 RRN 注释 | Done |
| AI-4 | `pushover.py` priority 参数化（Emergency→默认 High，新增 `priority: int = 1` 参数） | Done |
| DOC-audit | `2026-06-10-codebase-audit.md` 追加废弃提示 | Done |
| DOC-adr | `ADR-0020` 追加 required status checks 实测补注 + CI-2 缺口标注 | Done |

### HALT — 待人工决策

| ID | 描述 | 阻断原因 | gh api 草案 |
| --- | --- | --- | --- |
| CI-1 | 配置 `main` 分支 prod 环境 required reviewers | 需人工指定 reviewer GitHub 账号；不可臆测 | `gh api repos/{owner}/{repo}/environments/production/deployment_protection_rules --method POST -f type=required_reviewers ...` |
| CI-2 | 把 `Schema Digest Drift` 加入 `main` required status checks | 改生产分支保护；context 名须先从 CI 日志确认，不可推断 | `gh api repos/{owner}/{repo}/branches/main/protection/required_status_checks/contexts --method POST -f contexts[]="schema-digest-drift"` |
| AI-2 | `ai/requirements.txt` torch 钉版 | 正确版本依赖部署机真实 CUDA 版本；README 标 cu118，但 `torch==2.11.0+cu130` 不存在，禁止臆测 | 待人工提供部署机 CUDA 版本后执行 |
| SEC-SEED（history） | git history rewrite — 清除已推送 commits 中的明文 RRN 注释 | 影响已推送远端 refs，需人工决策是否重写历史（`git filter-repo` / BFG） | 待人工决策 |

---

## Wave 1（地基）

按顺序执行，后续 Wave 依赖本层完成。

| ID | 描述 | 前置 | 优先级 |
| --- | --- | --- | --- |
| DB-1 | initdb ↔ Flyway 对齐：`audit_logs` 列/可空、`status_enum` REJECTED、`notifications` 可空、`login_id` 去重 | 无（但须先于 DB-2、SEC-0026 种子改动） | P1 |
| BE-1 | 全局异常处理 + `@Valid` + 关 stacktrace | 无（软前置于 BE-3 和任何新增写端点） | P1 |

---

## Wave 2（安全加固，可并行）

各项可独立并行，但 AI 系三项（AI-1/AI-3/SEC-0026）建议一次进入 `ai/` 统一改动。

| ID | 描述 | 前置 | 优先级 |
| --- | --- | --- | --- |
| BE-2 | 休眠 Service 授权 `@PreAuthorize` | 无（独立于 DB-1） | P1 |
| BE-3 | VO 字段收窄（移除密码/RRN hash 等敏感字段） | 软前置 BE-1 | P1 |
| AI-1 | 推理同步阻塞 async 循环修复 | 与 AI-3/SEC-0026 协调同批进入 `ai/` | P2 |
| AI-3 | 推理入参范围校验 | 与 AI-1/SEC-0026 协调同批进入 `ai/` | P2 |
| DB-2 | `Announcement`/`ClassRoomAssignment` 补 `kindergarten_id` | DB-1 完成后 | P2 |
| CODEGEN | 移除 `pg-spring-crud-codegen`：需 ADR + 删模块 + 清 17 处引用 | 无（独立） | P2 |

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
| CI-4 | 前端测试 + AI 测试纳入 CI | 依赖 FE/AI 测试先行编写 | P3 |
| CI-3 | 分支保护加固 | CI-1/CI-2 完成后 | P3 |

---

## 残留已知风险

1. **SEC-SEED history**：`26_children_seed.sql` 中的明文 RRN 注释仍存在于 git history，需 history rewrite（Wave 0 HALT）。工作区文件已清理。
2. **AI-2 torch 版本**：`ai/requirements.txt` 中 torch 版本未钉定，构建时可能拉取非预期版本；需人工确认部署机 CUDA 版本后修复。
3. **CI-2 schema drift check**：`Schema Digest Drift` 尚未纳入 `main` required checks，schema drift 不能在 PR 合并前自动阻断。
