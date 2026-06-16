---
ADR: ADR-0020
title: "ADR-0020: 两层分支模型与发布门（develop 集成 trunk + main 人工发布）"
status: Accepted
implementation: Complete
date: 2026-06-15
deciders: 接手人起草，维护者 Accept（2026-06-15）
supersedes: []
superseded_by: null
related_specs:
  - SPEC-0001
---

# ADR-0020: 两层分支模型与发布门（develop 集成 trunk + main 人工发布）

## 状态（Status）

Decision: `Accepted`（2026-06-15 维护者 Accept）

Implementation: `Complete`

> 背景：维护者希望承担 Lead/Planner 的主 agent 能**长时间自主连续工作**，而现行"每个改动经 develop PR + 人工合并"的流程使主 agent 在每次合并处受阻。本 ADR 把发布门从 develop 迁移到 main，并固化 develop=集成 trunk、main=人工发布门的两层模型。

> 实施状态（2026-06-15）：(1) `.ai/project.md` 的「Branch And CI Workflow」「Delivery Gates」已对齐本模型；(2) GitHub `develop` 分支保护已去掉 require-PR（放开主 agent 直推），保持禁 force-push/删除、push 限维护者；(3) GitHub `main` 已新增 required status checks = `Gradle test (Java 21)` + `docker compose config`（strict=false），保留 require-PR + code-owner 审批 + restrictions + block_creations；(4) main 复活的首次 `develop → main` 发布（[PR #90](https://github.com/ai-kids-care-team/ai-kids-care/pull/90)）已于 2026-06-15 由维护者合并（merge commit `747df2e`），`main` 现已对齐 `develop` 基线。后续按本模型小批量勤发布。遗留：`main` 含一张旧名 ERD png（`docs/db/ERD/pics/AI-detection&evenet-process.png`），属可选清理项，非阻断。

> 注：`main` 上 required status check 的精确 context 名为 CI **job 名**（`Gradle test (Java 21)` / `docker compose config`），非 workflow 名（`Backend Java Tests` / `Compose Config`）；project.md 行文用 workflow 名指代，配置用 job 名。

## 背景（Context）

### As-built 事实（2026-06-15，经 `gh api` / `git` 核实）

- 默认分支为 `develop`。
- `develop` classic protection：要求经 PR 合并（`required_pull_request_reviews` 存在），但 `required_approving_review_count = 0`（**不要求任何批准**）；`required_status_checks = null`（**CI 不是合并前必过门**）；`enforce_admins = false`（管理员可绕过、即可直推）；push 限制为维护者账号；`allow_force_pushes = false`、`allow_deletions = false`。
- `main` classic protection：要求经 PR；`require_code_owner_reviews = true`（仓库根存在 `CODEOWNERS`，故 code-owner 审批**有效**）；`required_status_checks = null`；`enforce_admins = false`。
- `main` 落后 `develop` 418 个提交，且有 1 个 `develop` 没有的分叉提交（`7502d83 rename erd doc picture name`）；`main` 事实上为**废弃分支**，当前不存在在用的 `develop → main` 发布流。
- CI workflows `.github/workflows/backend-java-tests.yml` 与 `.github/workflows/compose-config.yml` 均 `on: push + pull_request`（分支 `develop`/`main`）+ `workflow_dispatch`，因此**直推 `develop` 也会触发 CI（事后）**。
- 现行 [`.ai/project.md`](../../../.ai/project.md) 的「Branch And CI Workflow」明文"**不得直推 `develop`**"、发布门走 `develop` PR + 人工批准；这与"主 agent 在 `develop` 上连续干活"直接冲突。

### 关键约束（事实）

- GitHub 的 `required status checks` 只能在 **PR/合并模型**下拦截；对**直推**只能在 push 后事后跑 CI，无法阻止已落地的提交。因此"主 agent 直推 `develop`"与"`develop` 上 CI 必过"在技术上**不可兼得**。
- 单人维护：SPEC-0001 要求的 "fresh independent review" 在本仓由**全新上下文的 reviewer agent** 承担（宪章多 agent 模型：评审者≠实现者），与维护者的最终 merge 决定**可分离**。

## 决策（Decision）

采用**两层分支模型**：`develop` 为 Lead/Planner 主 agent 的集成 trunk（允许直接提交/直推、sub-agent 分支在此汇入）；`main` 为受保护的人工发布线（PR + code-owner + CI 必过 + fresh independent review + 维护者拍板）。**约束性的 CI、独立评审与人工门集中在 `develop → main`，而非每次 `develop` 提交。**

边界：

1. **`develop`（集成 trunk）**：主 agent 可直接提交/直推；sub-agent 在隔离分支（并行改文件用 worktree 隔离）上实现，完成后汇入 `develop`。`develop` 是集成线，**允许暂时性破损**；GitHub Actions 在每次 push 事后运行，作为信号（无法拦截直推）。禁 force-push 与删除。
2. **sub-agent 分支 → `develop`**：实质性/安全敏感的改动在汇入前须经**一次 fresh-context 评审**（主 agent 或专职 reviewer sub-agent，**不得是产出该实现的会话**）；琐碎改动由主 agent 直接整合。
3. **主 agent 直推 `develop` 前**：运行 narrowest 可靠的本地检查（`git diff --check`、所改区域的 focused 测试/构建）。`develop` 的破损靠 CI 事后暴露 + 主 agent 自查，靠 `develop → main` 门最终拦截。
4. **`main`（发布线）**：经 PR 从 `develop` 发布，**小批量、勤发布**；release PR 必须满足——GitHub Actions 通过（`Backend Java Tests` + `Compose Config` 设为 required checks）、code-owner 审批、一个 fresh read-only reviewer 对声明基线与当前 worktree 评审（任一 P0–P3 finding 阻断）、`FINAL REVIEW: PASS`，最后由**维护者**做 merge 拍板。
5. **发布节奏**：保持 `develop → main` 小而勤，避免 `develop` 累积巨量未评审增量使人工门评审失控。
6. **main 复活**：以本模型的**首次 `develop → main` 发布**一次性完成（处理 `main` 的分叉提交 `7502d83`），同时验证整条发布流。

## 方案比较（Options）

| 方案 | 优点 | 代价/风险 | 结论 |
| --- | --- | --- | --- |
| A. 现状：每改动走 `develop` PR + 人工合 | 每次合并前可人工把关 | 与"主 agent 自主连续工作"冲突；且 `develop` 当前未设 CI 必过（真实缺口） | 否决 |
| B. `develop` 保留 require-PR + CI 必过 + auto-merge（去人工点击） | 安全最高、PR/CI 审计链完整、CI 可拦坏合并 | **与"主 agent 直接在 `develop` 干活"冲突**（主 agent 不能直推） | 否决（不符合主 agent trunk 诉求） |
| C. `develop` 直推（主 agent trunk）+ `main` 人工发布门 | 主 agent 零人工摩擦连续工作；`main` 成真正受保护发布线 | `develop` CI 事后、约束门集中在 `main`；依赖纪律（pre-push 本地检查、勤发布、sub 分支短命） | **采纳** |

## 后果（Consequences）

- **正面**：主 agent 在 `develop` 上连续工作无人工摩擦；`main` 成为受保护发布线（CI 必过 + code-owner + fresh review + 人工拍板）；独立评审在 `sub→develop` 与 `develop→main` 两处均可由 fresh agent 承担；复活的 `main` 提供稳定回滚点。
- **负面 / 代价**：`develop` 可能暂时性破损、回归由 CI 事后暴露；若 `develop → main` 发布不勤，人工门评审批次过大、风险集中；模型正确性依赖纪律（pre-push 本地检查、sub 分支短命勤整合、勤发布）。
- **影响范围**：GitHub 分支保护（`develop`/`main`）、`CODEOWNERS`、CI required checks、[`.ai/project.md`](../../../.ai/project.md) 的「Branch And CI Workflow」与「Delivery Gates」、所有后续 agent 的工作流、[SPEC-0001](../../specs/SPEC-0001-auth-authorization-tenant-sensitive-data-boundaries.md) 发布门的落点。

## 合规与验证（Compliance）

- GitHub `develop`：去掉 require-PR（允许主 agent 直推）；保持禁 force-push/删除。
- GitHub `main`：保留 require-PR + code-owner 审批；新增 required status checks = `Backend Java Tests` + `Compose Config`；保持禁 force-push/删除。
- 每次 `develop → main` 发布：CI 绿 + fresh reviewer agent `FINAL REVIEW: PASS` + 维护者 merge。
- `main` 复活后必须为 `develop` 的祖先（fast-forward 或显式 merge，含处理 `7502d83`）。
- [`.ai/project.md`](../../../.ai/project.md) 的「Branch And CI Workflow」「Delivery Gates」与本 ADR 一致（无文档漂移）。

## 关联（References）

- [SPEC-0001：认证、授权、租户与敏感数据边界](../../specs/SPEC-0001-auth-authorization-tenant-sensitive-data-boundaries.md)（其 Release Gate 落点依赖本分支模型）
- [`.ai/project.md`](../../../.ai/project.md)、[`.ai/CONTEXT.md`](../../../.ai/CONTEXT.md)（宪章 Risk Gates / Multi-Agent Coordination）
- 仓库根 `CODEOWNERS`、`.github/workflows/backend-java-tests.yml`、`.github/workflows/compose-config.yml`
- [ADR-0019：服务端有效授权上下文与租户强制边界](ADR-0019-effective-authorization-context-tenant-enforcement.md)
