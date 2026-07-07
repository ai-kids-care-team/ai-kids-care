---
name: development-orchestrator
description: 编排一条 plan→design→implement(前后端并行 fan-out/fan-in)→gate→archive 的开发流水线,实现一条 OpenSpec change。当用户要求"实现/开发某 change"、"按流水线做前后端实现"、"并行实现前后端"时使用。排除性区分:①"分析/审查工程现状/多角度健康度"→用 component-analysis-orchestrator,不是本 skill;②"只看当前未提交 diff"→用 /code-review;③纯 harness/agent/skill/hook/memory 自身变更→不走本 skill,由主循环手工维护(本项目手工维护 harness,无 harness meta-skill 插件);④只想生成 change 的 proposal/design/tasks 不实现→用 openspec propose。本 skill 是"把一条已 propose 的 change 实现落地"。
---

# 开发流水线编排器(Development Orchestrator）

把一条已 propose 的 OpenSpec change 实现落地:`dev-lead`(opus)领队,fan-out 前后端专职 implementer(sonnet)在各自 git worktree **并行实现**,fan-in cherry-pick 到 `develop`,经**分层门禁**收口后触发 archive。形态对称于审查侧的 `component-analysis-orchestrator`。

> **执行模型(诚实化)**:本环境只有 `Agent` / `SendMessage`(续聊已 spawn 的子代理)/ `Task*`,**没有 `TeamCreate`**,无法让多个并发运行的 agent 实时互发消息。因此编排是一个**显式 DAG**:`Agent` 并行 fan-out 实现者(彼此独立,不假装能实时互通)→ lead 收齐后在 fan-in 阶段 cherry-pick 收口 + 驱动门禁(跨侧互证由 lead 对照双侧产物 + `integration-analyst` 完成,对存疑点可发起第二轮定向 `Agent`)。

## 阶段拓扑(总 DAG)

```
[plan+design]              [implement — fan-out/fan-in]              [gate]                    [archive]
openspec-propose   ──►   ┌─ backend-implementer  (worktree A) ─┐  ──► ①硬测试门 ──►            openspec-
产出 change:             │                                     │      ②/code-review           archive-change
 design + specs +        ├─ frontend-implementer (worktree B) ─┤      ③安全+集成定向复核
 tasks + 【API 契约】     └───────────────(并行)────────────────┘      ④自修回路 → 清零
   (单点, 串行前置)              (同层, 全并行)                        (串行收口)               (串行)
```

- **plan / design / archive = 复用 OpenSpec**(`openspec-propose` / `openspec-archive-change`),不新造,符合「做什么用 OpenSpec」范式。
- 只有中间 **implement + gate** 是本 skill 新造的编排。

## 角色与模型分配(Agent 调用时按此显式传 `model`)
| 角色 | 模型 | 新造? | 立场 |
|------|------|------|------|
| dev-lead | opus | 🆕 | 领队:建 DAG / fan-out / 驱动门禁 / fan-in / 触发 archive |
| backend-implementer | sonnet | 🆕 | 只写 `backend/`,TDD,守多租户/CSRF/授权/MapStruct |
| frontend-implementer | sonnet | 🆕 | 只写 `frontend/`,对契约接线,双客户端 CSRF,不传 tenantId |
| security-analyst | opus | ♻️ 复用 | 门禁定向复核 —— sonnet 后端的安全承重墙 |
| integration-analyst | opus | ♻️ 复用 | 门禁验契约双侧吻合(后端 DTO/VO ↔ 前端 api.ts) |
| Explore | — | ♻️ 复用 | 需要时补架构上下文 |

**语言约定**:dev-lead 对用户中文为主、英文术语为辅;对子代理的 `Agent` prompt / `SendMessage` 可用英文保语义精确;产出文件与代码注释按各自既有风格,保留代码标识符/API 路径/enum/DB 名/韩语文案不变。

## Phase 0:上下文确认(先做)
- 确认目标 change **已 propose**:存在 `openspec/changes/<change-id>/`,含 design + tasks。缺 change → 先走 `/opsx:propose`(不是本 skill 的职责)。
- 确认 **`api-contract.md`** 存在:注意 **`/opsx:propose` 不产此文件**(OpenSpec schema 无此 artifact),它是 design 阶段**手工冻结**、放进 change 目录的产物(对照 `references/api-contract-template.md`)。缺则须手写补齐,非重跑 propose。
- 判断 change 类型:**纯后端 / 纯前端 / 全栈** → 决定开几个 lane(全栈 1 BE + 1 FE;纯一侧只开该侧)。
- change 已部分实现(后续/自修再入)→ 先做 `git status` 时效判定,只重跑未完成/被打回的 lane,复用其余。

## Phase 1:契约冻结确认(并行前提)
- 核对 `openspec/changes/<change-id>/api-contract.md` **存在且字段级完整**,对照 `references/api-contract-template.md` 的结构(端点/鉴权/DTO/VO/可空性/enum/分页/错误/前端对齐点)。
- **契约缺失或含糊 → 回 design 补清**(**手写** `api-contract.md`,对照 `references/api-contract-template.md`;非重跑 `/opsx:propose`),**不退化成后端先行**。字段级完整的契约是双侧并行的唯一真源。

## Phase 2:fan-out 实现(全并行)
1. dev-lead 用 `Agent` 工具**并行 spawn** `backend-implementer` 与 `frontend-implementer`(各在独立 worktree,`.claude/worktrees/`;各按分配传 `model=sonnet`;可 `run_in_background`)。
2. 每个 `Agent` prompt **自包含**:负责组件、change 路径、**冻结契约路径**、需实现的 tasks 子集、TDD 要求、本侧硬约束(后端多租户/CSRF/授权;前端不传 tenantId/双客户端 CSRF)。
3. 实现者**彼此独立、不互相通信**;跨侧疑问记进各自 notes,由 dev-lead 在 fan-in 核对。fan-out 前提细节见 `references/fan-in-playbook.md`。

## Phase 3:fan-in 收口(串行)
- 按 `references/fan-in-playbook.md`:每批开工前核对 `git status` 防 clobber → 批次间先 commit → 把两 worktree 提交 **cherry-pick 到 `develop`** → 冲突由 dev-lead 裁决(前后端文件域基本不重叠,优先保契约一致侧)。
- cherry-pick 后立即进 Phase 4 门禁(合并后的 develop 状态才是门禁对象)。

## Phase 4:分层门禁 + 自修回路(串行收口)
按 `references/gate-checklist.md` 顺序执行,任一层不过则回路。因两端 implementer 都是 sonnet,门禁是**质量承重墙**,③ 强制非可选:
1. **①硬测试门**:后端 `cd backend && ./gradlew test`(DooD:挂 repo 根 + `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`);前端 `cd frontend && npm run lint && npm run build`;改 `db/initdb/` seed → `./gradlew cleanTest test`。
2. **②`/code-review`**:对合并 diff 做通用正确性/复用审查。
3. **③定向复核**:并行 spawn `security-analyst`(opus,认证授权/多租户/PII·密钥/注入·CSRF)+ `integration-analyst`(opus,契约双侧逐字段吻合/SSE·事件协议/enum 三处同步)。
4. **④自修回路**:findings 回**对应 implementer** 自修 → 重跑 ①–③ 直到清零。**high-risk 一律 halt 等维护者批准**;exhausted 仍未清零 → dev-lead 自验+提交剩余修正+收口报告如实标注。

## Phase 5:archive(串行)
- 门禁清零后触发 `openspec-archive-change`,把 change(含 `api-contract.md`)归档。
- 破坏性变更(schema/迁移/删除)在 apply/archive 前须经维护者**逐个批准**。

## 数据传递协议
- **返回值级**(`Agent` 返回):实现者/复核者把 top 摘要 + worktree/文件路径 + notes 返回给 dev-lead。
- **续聊级**(`SendMessage`):仅用于 dev-lead 对某个**已 spawn** 的子代理追加澄清/补证(非并发 agent 间实时互通——那不存在)。
- **文件级**:change 目录(`openspec/changes/<id>/`)→ 各 worktree → `develop`;契约 `api-contract.md` 贯穿始终。

## 错误处理
- 实现者/复核者 1 次重试仍失败 → 不阻塞,收口报告标注该侧/该项缺失,用其余成文。
- 契约含糊 → 回 design;需 schema/破坏性变更 → 等维护者逐个批准。
- 无 `TeamCreate` → 无「团队模式」可降级,一律 `Agent` fan-out + lead 合并。
- **环境事实**:本机 **node v24 在 PATH**(前端 lint/build 可原生跑,docker 仅回退);**Java 无** → 后端 testcontainers 走 DooD 容器。

## 测试场景
- **全栈正常流**:已 propose 好契约的 change → dev-lead 契约确认 OK → 并行 fan-out BE+FE 两实现者(各 worktree)→ cherry-pick 到 develop → 门禁 ①②③ 全绿 → archive。
- **契约含糊流**:Phase 1 发现 `api-contract.md` 某响应字段缺可空性标注 → dev-lead **回 design 补契约**(不让实现者臆断)→ 补齐后再 fan-out。
- **门禁 halt 流**:门禁 ③ `security-analyst` 报某新查询「加载后过滤租户、疑越权」为 high-risk → **halt 等维护者批准**修复方向 → 批准后 backend-implementer 自修(改 JPQL 加 `kindergarten_id` 谓词)→ 重跑门禁清零。
- **纯前端 change**:只开 FE lane(backend-implementer 不 spawn);门禁 ① 只跑 `npm run lint && npm run build`,③ 仍跑 integration-analyst 验前端对既有契约的接线是否吻合。
- **自修再入流**:门禁打回 2 条 findings → dev-lead 只对 backend-implementer 续派增量修订(`SendMessage`/新 `Agent` 带反馈点)→ 复用前端已过 lane → 重跑门禁受影响层。
