# 组件多角度分析 Harness v2 — 设计文档

日期：2026-06-25 ｜ 状态：已批准设计，待 writing-plans ｜ 前身：v1（commit 7cb3ad7，2026-06-25）

## 1. 动机（来自 v1 首跑暴露的短板）

v1 已落地（4 角度 + lead，sub-agent fan-out，2026-06-25 首跑产出 1 critical + 12 high）。首跑暴露四个可改进点，构成 v2 范围：

1. **可信度**：安全/集成多条结论是 `confidence=medium` 的纯静态推断（本机无 Java/Node），无独立复核，假阳性风险未被压制。
2. **角度粒度**：最大一簇 high 全是扩展性/性能/实时性（ARC-01/02/03、INT-04），被硬塞进"架构"角度，淹没了纯结构问题。
3. **验证手段**：全静态，报告"覆盖与局限"是最大遗憾——无动态坐实能力。
4. **视角单一**：v1 的角度全是"由内向外"的技术视角，缺一个"由外向内"的真实使用者视角；本次 INT-07/QLT-01/INT-01/QLT-08 从用户席位看都是"功能没兑现"，但 v1 只从技术根因角度命中，未从用户影响定标。

## 2. v2 目标与非目标

**目标**
- 把分析角度从 4 扩到 6（新增 performance、experience）。
- 引入对抗式验证阶段，压制假阳性，覆盖 high + medium。
- 引入分档运行（轻量 / 标准 / 深度），成本可控。
- 标准/深度档升级为真 agent team（实时互证）；深度档具备 DooD 动态验证能力。

**非目标**
- 不改 v1 的 finding schema 主体（仅新增 `verification` 字段）。
- 不做自动修复（findings → 修复闭环留作未来与 main 上 planner/implementer harness 对接）。
- 不替换 `/code-review`（其看 diff，本 harness 看组件/工程现状，分工不变）。

## 3. 架构总览

**阵容**：6 角度分析师 + 1 验证者 + 1 领队。

| 角色 | 模型 | 立场 | id 前缀 |
|------|------|------|---------|
| architecture-analyst | sonnet | 纯结构：分层/依赖/耦合/模式（**移除性能**） | ARC- |
| quality-analyst | sonnet | 可维护性/复杂度/重复/死代码/技术债/测试质量 | QLT- |
| security-analyst | opus | 认证授权/多租户/PII·密钥/注入/审计 | SEC- |
| integration-analyst | opus | 跨组件契约双侧交叉比对 | INT- |
| **performance-analyst**（新） | opus | 扩展性/性能：N+1、事务内 IO、线程池、SSE/事件背压、多实例去重、延迟 | PRF- |
| **experience-analyst**（新） | opus | 用户/功能视角：脱离代码，问"使用者的任务办成了吗" | UX- |
| **finding-verifier**（新） | opus（general-purpose，可执行） | 对抗式复核：默认尝试反驳；深度档用 DooD 实跑坐实 | —（回写 verification） |
| analysis-lead | opus | 选档/组队/监控/去重裁决定级/综合 verified findings | — |

**单元边界（每个单元一个清晰职责、可独立理解与测试）**
- 每个角度分析师 = 一个 skill（怎么看）+ 一个 agent（谁看），只产出本角度 findings。
- 验证者 = 单一职责"确认/反驳一条 finding"，两种方法（静态推理 / DooD 执行），不关心 finding 来自哪个角度。
- 领队 = 选档 + 团队协调 + 综合，不亲自做某角度深挖。
- DooD 配方 = 隔离在 `references/dood-recipe.md`，被验证者复用。

## 4. 分档运行形态（执行模式随档变 = hybrid）

| 档 | 角度 | 执行模式 | 验证 | 用途 |
|---|---|---|---|---|
| **轻量 light** | 6 | sub-agent fan-out（无团队开销） | 无 | PR 前快扫、定期体检 |
| **标准 standard**（默认） | 6 | **真 agent team**（TeamCreate + SendMessage 实时互证） | 静态对抗：每条 **high+medium** 由 finding-verifier 以反驳为默认假设复核 1 次 | 常规审查 |
| **深度 deep** | 6 | 真 agent team | 静态升级为 3 票/多 lens refute（majority 反驳即降级） **+ DooD** 对可动态验证项实跑坐实 | 发版前、关键审计 |

档位在 orchestrator 的 Phase 0.5 选择：用户显式指定 > 默认标准。轻量/深度需用户点名或满足触发词（"快扫/快速体检" → 轻量；"深度/发版前/关键审计" → 深度）。

## 5. 验证阶段（A 静态 + C 动态）

**触发范围**：severity ∈ {critical, high, medium}。low/info 跳过（成本/收益不划算）。

**A — 静态对抗（所有非轻量档默认）**
- finding-verifier 拿到一条 finding，**默认假设它是假阳性**，主动找反证（代码反读、上下文、是否演示/seed 误报、推断链是否成立）。
- 标准档：1 票。深度档：3 票或多 lens（correctness / 误报-as-design / 可复现）；majority 反驳 → verdict=refuted。
- 产出 verdict：confirmed | refuted | unverified（证据不足）。

**C — 动态 DooD（仅深度档，对"可动态验证"的 finding）**
- 适用类型：测试缺口（实跑 testcontainers 看是否真缺/真红）、契约错位（构造调用比对）、构建/lint（前端 node:20 容器）。
- 配方（`adversarial-verification/references/dood-recipe.md`）：挂 **repo 根**（非 backend 子目录）+ `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` + Ryuk 关 + 挂 docker socket；改 seed 类验证须 `gradle cleanTest`；前端 lint/build 用 node:20 容器并在提交前还原 `next-env.d.ts`。
- 无 docker / 跑不起来 → 回退静态，verdict 维持 unverified-dynamic，**报告显式标注未动态坐实**。

**回写**：finding 新增字段
```yaml
verification:
  verdict: confirmed | refuted | unverified
  method: static | dood
  votes: "<n>/<m> confirm"   # 深度档多票时
  note: <一句话：反驳理由或坐实证据>
```
lead 只综合 `confirmed`；`refuted` 移入报告附录（保留出处，不删，附反驳理由）；`unverified` 标疑列出。

## 6. 数据流

```
[Phase 0.5 选档] → [Phase 1 架构地图(复用或 Explore 生成)]
   ↓
[Phase 2 分析]  轻量: 6 sub-agent fan-out
                标准/深度: TeamCreate 6 分析师 → SendMessage 实时互证
   ↓ 各写 _workspace/{angle}_findings.md
[Phase 3 验证]  finding-verifier 对每条 high/medium 复核(静态恒开; 深度档+DooD)
   ↓ 回写 verification 字段 → _workspace/{angle}_findings.verified.md
[Phase 4 综合]  lead 去重/裁决/统一定级 → _workspace/00_analysis_report.md
```
- 传递：任务级(TaskCreate 跟踪 6 角度+验证依赖) + 文件级(_workspace 产出) + 消息级(SendMessage 实时互证，**可用英文保语义精确**)。
- 语言：lead↔用户中文为主英文为辅；团队内 SendMessage 可英文；产出文件中文为主。

## 7. experience-analyst 细节（用户/功能视角）

- **立场**：脱离架构/代码，只问"作为使用者，我的任务办成了吗"。是唯一"由外向内"的角度，与 5 个技术角度（architecture/quality/security/integration/performance）三角互证。
- **镜头**：① 角色化旅程（家长/教师/园长 KG_ADMIN/平台 IT 管理员/超管，各自核心任务端到端能否走通）② 功能完整性（后端有能力却无前端入口 / 入口在但功能坏 / 关键功能缺失）③ 流程连贯性断点（注册→审批→登录→使用；告警→复核→家长通知 用户是否感知）④ 反馈可理解性（错误是否人话、空/加载/失败有无出路）⑤ 价值兑现（平台承诺"实时检测+及时通知家长"用户实际拿到没）。
- **手法**：读前端路由/页面/状态 + README/spec 理解"承诺的功能"，走查每角色 happy/失败路径；**不读后端实现做评判**，但可 SendMessage 问 integration-analyst「这入口背后接通没」。
- **定标**：severity 按**用户影响**重定（代码里的 low 可能是用户的 high）；cross_refs 链到其余角度的技术根因。

## 8. 错误处理

- 分析师/验证者 1 次重试仍失败 → 不阻塞，报告标注该角度/该项缺失。
- DooD 不可用 → 回退静态，标 unverified-dynamic。
- 分析师与验证者冲突 → lead 裁决并并列出处，不单方删除。
- 团队模式（标准/深度）若 TeamCreate 不可用 → 自动降级为 fan-out（等效轻量执行 + 仍跑验证），并在报告注明降级。

## 9. 落地文件清单

**新增**
- `.claude/agents/performance-analyst.md`（opus）
- `.claude/agents/experience-analyst.md`（opus）
- `.claude/agents/finding-verifier.md`（opus，general-purpose）
- `.claude/skills/analyze-performance/SKILL.md`
- `.claude/skills/analyze-experience/SKILL.md`
- `.claude/skills/adversarial-verification/SKILL.md` + `references/dood-recipe.md`

**修改**
- `.claude/skills/analyze-architecture/SKILL.md`（移除性能段 → 纯结构；与 performance 靠 cross_refs 互链）
- `.claude/skills/component-analysis-orchestrator/SKILL.md`（Phase 0.5 选档、团队模式、Phase 3 验证、6 成员、数据流）
- `.claude/skills/component-analysis-orchestrator/references/finding-schema.md`（加 `verification` 字段、6 个 id 前缀、UX 用户影响定标说明）
- `.claude/skills/component-analysis-orchestrator/references/report-template.md`（加「验证」列 + refuted 附录）
- `.claude/agents/analysis-lead.md`（选档/验证阶段/综合 verified）
- `CLAUDE.md`（触发词加档位关键词、变更历史加 v2 行）

## 10. 验证（本 harness 自身的测试场景）

- **正常流（标准档）**：6 角度组队并行 → experience 发现"家长看不到通知历史"，SendMessage 问 integration → integration 回"后端 GET /notifications 就绪但前端无 api 文件"（INT-07 同根，cross_ref 互链）→ verifier 静态复核 confirmed → lead 合并为一条多角度佐证的 high。
- **误报压制流**：security 报"seed 含弱 pepper 哈希"（SEC-05 样）→ verifier 静态复核发现是 test pepper 且不进生产路径 → verdict=refuted，移入附录附理由 → 不污染主报告。
- **降级流（深度档无 docker）**：verifier 欲 DooD 实跑 testcontainers 验证"tenant 隔离测试缺口"→ docker 不可用 → 回退静态，标 unverified-dynamic → 报告"覆盖与局限"列出需人工实跑确认。
- **轻量档**：6 sub-agent fan-out，无验证，产出快版评分 + 未验证 findings，明确标注"未经验证"。

## 11. 团队规模与成本说明

6 分析师 + 验证者属"大规模"（harness 指南 5–7 人上限），协调开销随之上升，故用分档控制：轻量无团队无验证、标准单票验证、深度才上多票+DooD。报告须 `log` 任何因档位/工具缺失导致的覆盖缺口，避免"看起来全覆盖实则没有"。
