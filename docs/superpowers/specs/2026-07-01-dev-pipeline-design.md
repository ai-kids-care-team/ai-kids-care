# dev-pipeline 开发流水线设计

> 状态:设计已经头脑风暴定稿,待落地实现。
> 日期:2026-07-01
> 关联:形似 `component-analysis-orchestrator`(审查流水线);挂在 OpenSpec change 生命周期上。

## 1. 目标与非目标

### 目标
建立一条 **plan → design → implement(前后端并行 fan-out/fan-in) → code review → archive** 的开发流水线,形态对称于现有的 `component-analysis-orchestrator`(审查侧),补上工程一直缺的**实现侧编排**:

- 把 OpenSpec 的 `propose → apply → archive` 与 superpowers 的实现/审查 skill **端到端串成一条可重复触发的编排**(现在每阶段靠手动逐个触发)。
- 新增工程一直缺的**专职 implementer agent**(backend / frontend),把记忆里的临时 `parallel-apply-orchestration` 手法**固化为一等公民**。
- implement 阶段前后端**并行扇出**,各自专注一侧,fan-in 收口。

### 非目标(YAGNI)
- **不重造 plan/design/archive**:直接复用 `openspec-propose` / `openspec-archive-change`,符合 CLAUDE.md「做什么用 OpenSpec」范式。
- **不做多实例扩展相关的任何事**(维护者已明确无限期搁置)。
- **不替代 `component-analysis-orchestrator`**:那是「审查工程现状」,本流水线是「实现一条 change」;门禁阶段**复用**其分析师,但不整包跑六角度团队(除非维护者对关键 change 显式要求)。
- 单条 change 内**不再细分**多个前端并行 lane(默认 1 BE + 1 FE);需要时另议。

## 2. 阶段拓扑(总 DAG)

```
[plan+design]              [implement — fan-out/fan-in]              [gate]                    [archive]
openspec-propose   ──►   ┌─ backend-implementer  (worktree A) ─┐  ──► ①硬测试门 ──►            openspec-
产出 change:             │                                     │      ②/code-review           archive-change
 design + specs +        ├─ frontend-implementer (worktree B) ─┤      ③安全+集成定向复核
 tasks + 【API 契约】     └───────────────(并行)────────────────┘      ④自修回路 → 清零
   (单点, 串行前置)              (同层, 全并行)                        (串行收口)               (串行)
```

- plan/design/archive = **复用 OpenSpec**,不新造。
- 只有中间 implement + gate 是新造编排。
- 编排形态对称 `component-analysis-orchestrator`:一个 `development-orchestrator` skill + 一个 `dev-lead`(opus)领队,fan-out 实现者、fan-in 收口。
- 执行模型与审查侧一致:本环境**只有 `Agent` / `SendMessage` / `Task*`,无 `TeamCreate`**;编排是显式 DAG 的 `Agent` fan-out + lead 合并,不假装并发 agent 能实时互通。

## 3. Agent 矩阵(新造 / 复用)

| 角色 | 模型 | 新造? | 职责 |
|---|---|---|---|
| **dev-lead** | opus | 🆕 | 领队:读 change/契约 → 建执行 DAG → fan-out 实现者 → 驱动分层门禁 → fan-in cherry-pick 收口 → 触发 archive |
| **backend-implementer** | **sonnet** | 🆕 | 只写 `backend/`;TDD;严守多租户隔离(JPQL 层 `kindergarten_id` 谓词)/CSRF/`@PreAuthorize` 标在 service/MapStruct 命名约定;对着冻结契约实现 |
| **frontend-implementer** | **sonnet** | 🆕 | 只写 `frontend/`;对着冻结契约接线 `services/apis/*`、RTK Query/Axios 双客户端、CSRF 回填;不传 kindergartenId |
| security-analyst | opus | ♻️ 复用 | 门禁定向复核 —— Sonnet 后端的安全承重墙 |
| integration-analyst | opus | ♻️ 复用 | 门禁验契约双侧吻合(后端 DTO/VO ↔ 前端 api.ts) |
| Explore | — | ♻️ 复用 | 需要时补架构上下文(缺架构地图则先派) |

**模型选择理由**:两端 implementer 都用 **sonnet**(维护者定,取成本/并行吞吐);其连带效应是**门禁成为质量承重墙**——因此门禁强制包含 opus 的 security-analyst + integration-analyst 定向复核,而非可选。

**语言约定**(沿用审查侧):dev-lead 对用户中文为主;对子代理的 `Agent` prompt / `SendMessage` 可用英文保语义精确;产出文件与代码注释按各自既有风格。

## 4. 契约先行(Contract-first)

`openspec-propose` 的 design 阶段**额外冻结**一份 API 契约产物:

- 位置:`openspec/changes/<change-id>/api-contract.md`(随 change 走,archive 时一并归档)。
- 内容:每个受影响端点的 路径 / HTTP 方法 / 请求 DTO 字段 / 响应 VO 字段 / 字段可空性 / enum 值 / 分页 shape(Spring `Page` ↔ 前端 `PageResponse`)/ 鉴权(会话+CSRF 或 internal Bearer)。
- 作用:前后端唯一真源。`backend-implementer` 与 `frontend-implementer` **各自对着这份冻结契约全并行实现**,互不阻塞。
- fan-in 时 `integration-analyst` 逐字段比对双侧实现是否都贴合契约(QA 式双侧同读)。
- **契约含糊 → 回到 design 补清**,不退化成「后端先行」。这是本流水线并行度的前提。

## 5. Fan-out / fan-in 机制

### Fan-out
- 每个实现者在**独立 git worktree**(`.claude/worktrees/`)工作,互不踩踏。
- 单条 change 默认 **1 后端 lane + 1 前端 lane**;纯后端 / 纯前端 change 则只开一侧。
- 每个实现者 `Agent` prompt 自包含:负责组件、change 路径、冻结契约路径、需实现的 tasks 子集、TDD 要求、本侧的关键约定(后端多租户/CSRF/JPQL;前端不传 tenantId/双客户端 CSRF)。
- 实现者彼此独立、不互相通信;跨侧疑问记进各自产出的 notes,由 dev-lead 在 fan-in 核对。

### Fan-in
- dev-lead 把两个 worktree 的提交 **cherry-pick 到 `develop`**(沿用记忆里已跑通的 parallel-apply 收口手法),冲突由 lead 裁决。
- **踩踏防护**(记忆:parallel-apply「脏 worktree 会 clobber」)——每批开工前 dev-lead 核对 `git status`;批次间先 commit,再进下一轮门禁/修复。

## 6. 分层门禁 + 自修回路

fan-in 后按序执行,任一层不过则回路:

1. **硬测试门**(不绿不放行):
   - `cd backend && ./gradlew test`(需 Docker/testcontainers;本机无 Java → 走 DooD 容器,挂 repo 根 + `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`)。
   - `cd frontend && npm run lint && npm run build`(本机有 node v24 可原生跑;回退 `node:20` 容器)。
   - 若改了 `db/initdb/` seed → 必须 `./gradlew cleanTest test`(seed 即 fixture,不在 test 输入会被判 UP-TO-DATE)。
2. **`/code-review`** 对合并 diff 做通用正确性/复用审查。
3. **定向复核**(因后端 Sonnet 强制,非可选):
   - `security-analyst`(opus):认证授权、多租户隔离、PII/RRN/密钥、注入/CSRF。
   - `integration-analyst`(opus):契约双侧吻合、字段错位、SSE/事件协议、enum 三处同步。
4. **自修回路**:findings 回给**对应 implementer**自修 → 重跑 1–3 直到清零。
   - **high-risk 一律 halt 等维护者批准**(沿用记忆 implement-review-loop 的 halt 约定:批准后编辑 run 脚本 halt 块 + 带同样 args resume 放行)。
   - 回路 **exhausted 仍未清零** → dev-lead 自验 + 提交剩余修正,并在收口报告**如实标注**未清零项(记忆:loop exhausted 末轮 fix 常留工作树未提交,Lead 须自验+提交)。

## 7. 交付物清单(落地时要新建的文件)

- `.claude/skills/development-orchestrator/SKILL.md`
  - `references/api-contract-template.md`(契约产物模板)
  - `references/gate-checklist.md`(分层门禁清单)
  - `references/fan-in-playbook.md`(cherry-pick 收口 + 冲突裁决 + 踩踏防护)
- `.claude/agents/dev-lead.md`(opus)
- `.claude/agents/backend-implementer.md`(sonnet)
- `.claude/agents/frontend-implementer.md`(sonnet)
- `.claude/commands/`(薄命令入口,如 `/dev-pipeline`,委托到 skill)

## 8. 已决策记录(头脑风暴结论)

| # | 决策点 | 结论 |
|---|---|---|
| 1 | 与 OpenSpec 关系 | **混合**:复用 OpenSpec propose/archive 做 plan/design/archive,新造 implement 编排 + BE/FE implementer |
| 2 | 前后端契约协调 | **契约先行**:design 冻结 API 契约产物,双方对着它全并行,fan-in 由 integration 校验 |
| 3 | implementer 模型 | **两端都 Sonnet**(门禁作质量承重墙兜底) |
| 4 | 门禁形态 | **分层**:硬测试门 → /code-review diff → 安全+集成定向复核 → implementer 自修回路;high-risk halt |
| 5 | 触发方式 | skill + 薄命令入口(默认) |
| 6 | 是否强制 TDD | 是,`test-driven-development`,硬测试门兜底(默认) |
| 7 | 前端多 lane | 先不做,单 change 1 BE + 1 FE(默认) |

## 9. 未决 / 风险

- **触发边界**:`development-orchestrator` 与 `component-analysis-orchestrator`、`/code-review`、裸 OpenSpec skill 的触发词要划清,避免误触(实现时在 SKILL.md description 写排除性区分,照抄审查侧的写法)。
- **两端 Sonnet 的漏网风险**:后端 Sonnet 碰安全 invariant 有漏网可能,完全依赖门禁的 security-analyst 兜底;若实践中发现漏网率偏高,可把 backend-implementer 升 opus(仅改 agent frontmatter,不动流水线)。
- **DooD 测试门耗时**:硬测试门在本机走容器,后端全套件较慢;可考虑按 change 影响域跑子集,但默认跑全套保正确性。
