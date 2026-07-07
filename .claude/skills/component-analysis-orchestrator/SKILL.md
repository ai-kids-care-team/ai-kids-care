---
name: component-analysis-orchestrator
description: 编排六个分析师并行 fan-out 对本工程做架构/质量/安全/集成/性能/用户六角度审查、对抗式验证并综合成报告。当用户要求"分析工程/组件"、"多角度分析"、"代码审查/健康度评估"、"架构/安全/质量/集成/性能/用户体验审查"，或"重新分析/再跑一遍/更新分析/只重看 X 组件/基于上次结果改进"、或指定"快扫/快速体检"(轻量)、"深度/发版前/关键审计"(深度)时，使用本 skill。**审查对象是业务工程组件（backend/frontend/ai/db/infra）的现状**。排除性区分：①审查对象是 harness / agent / skill / hook / memory 自身（点检/审计/同步 harness 配置）→ 不走本 skill，由主循环手工审计（本项目手工维护 harness，无 harness meta-skill 插件）；②只看当前未提交的 diff → 用 `/code-review`，不是本 skill。
---

# 组件多角度分析编排器（Orchestrator v3）

把六个角度的专家并行 fan-out，对 AI Kids Care 工程（backend / frontend / ai / db / infra）做多角度审查，经**对抗式验证**压制假阳性后，由 lead 在**交叉合并阶段**综合成一份决策者可读的报告。

> **执行模型（v3，诚实化）**：本环境只有 `Agent` / `SendMessage`（续聊已 spawn 的子代理）/ `Task*`，**没有 `TeamCreate`**，无法让多个并发运行的 agent 实时互发消息。因此编排是一个**显式 DAG**：`Agent` 并行 fan-out 分析师（彼此独立，**不假装能实时互证**）→ lead 收齐后在「交叉合并阶段」做去重/裁决/互证（对存疑点可发起第二轮定向 `Agent`）。原先 v2 写的「真团队 + 实时互证」依赖不存在的 `TeamCreate`、实跑时一直在静默降级 fan-out，v3 把文本改成与实际一致。

## 角色与模型分配（Agent 调用时按此显式传 `model`）
| 角色 | 模型 | 立场 |
|------|------|------|
| architecture-analyst | opus | 纯结构：分层/依赖/耦合/模式 |
| quality-analyst | opus | 可维护性/复杂度/重复/测试质量 |
| security-analyst | opus | 认证授权/多租户/PII·密钥/注入/审计 |
| integration-analyst | opus | 跨组件契约双侧交叉比对 |
| performance-analyst | opus | 扩展性/性能：N+1/事务内 IO/线程池/背压/多实例 |
| experience-analyst | opus | 用户/功能视角（唯一由外向内） |
| finding-verifier | opus | 对抗式复核（静态 + 深度档 DooD） |
| analysis-lead | opus | 选档/组队/监控/去重裁决定级/综合 |

**语言约定**：lead 对用户输出中文为主、英文为辅；对子代理的分派/追问（`Agent` prompt、`SendMessage` 续聊）可用英文以保语义精确；产出文件（findings / 报告）中文为主。

## Phase 0：上下文确认（先做）
判断初次 / 后续 / 部分再执行：
- `_workspace/` 不存在 → **初次执行**：走完整 Phase 1–4。
- `_workspace/` 存在 + 用户要求局部修订/补充某角度 → **部分再执行**：只重启相关分析师，复用其余 `*_findings.md`。
- `_workspace/` 存在 + 用户给了新范围/新输入 → **全新执行**：旧 `_workspace/` 改名 `_workspace_prev/`，重头来。

> **复用前必做时效判定（别只看目录在不在）**：`_workspace/` 里的 findings/报告**会过期**——其引用的 `file:line` 与结论基于写入时的 commit。复用任何旧产物前，lead 先看其文件日期/对应 commit：若**早于当前 HEAD 的相关改动**或**跨了 commit**，一律标记「可能失效、需重核」而非直接复用，并据此决定是重跑该角度还是仅增量。陈旧产物当真值会污染召回（参见 Phase 4b）。

确认**范围**：默认全 5 组件 + 全 6 角度；用户限定（如「只看 backend 安全」）则缩小。

## Phase 0.5：选档（只决定验证强度，与执行拓扑正交）
用户显式指定 > 触发词 > 默认**标准**。三档的**执行拓扑相同**（都是 fan-out DAG，见 Phase 1.5），差异只在**验证强度**——别再用「是否组团队」区分档位（团队从来跑不起来）。

| 档 | 角度 | 验证 |
|---|---|---|
| **轻量 light**（"快扫/快速体检"） | 6 | 无（产出明确标注"未经验证"） |
| **标准 standard**（默认） | 6 | finding-verifier 对每条 **critical+high+medium** 静态复核 1 次 |
| **深度 deep**（"深度/发版前/关键审计"） | 6 | 静态升级 3 票/多 lens + **DooD** 对可动态验证项实跑坐实 |

## Phase 1：准备架构地图
若无现成架构地图：派 `Explore` 子代理产出 `_workspace/architecture_map.md`（组件→技术栈/入口/关键目录/通信方式）。已有则复用。

## Phase 1.5：执行拓扑选择（与档位正交）
打散任务前，lead 先据**任务依赖关系**建一个显式 DAG，决定哪层并行、哪层串行。规则：

- **同层无依赖的节点一律并行 `Agent` fan-out**；仅在存在真实数据依赖处串行。
- **pipeline（流式）只出现在有前后数据依赖的环节**，不是默认形态。
- 本分析的标准 DAG：
  ```
  Explore 架构地图 ──► [六角度分析师]  ──► [按 finding 的验证]  ──► lead 交叉合并 + 综合
   (单点, 串行前置)    (同层, 全并行)      (同层, 静态全并行;        (单点, 串行收口)
                                          DooD 类单独排队)
  ```
  六角度之间**无依赖** → 全并行；分析→验证、验证→综合是**硬依赖** → 串行跨层；验证内各 finding **无依赖** → 并行（见 Phase 3）。
- 若本次任务形状不同（如只跑 2 个角度、或角度间被用户指定了前后依赖），据实重建 DAG，并把**实际采用的拓扑**写进报告「覆盖与局限」，让决策者看到真实调度。

## Phase 2：分析（fan-out，全档一致）
1. 用 `Agent` 工具**并行 fan-out** 六个分析师（独立子代理，各按模型表传 `model`，可 `run_in_background`）。分析师**彼此独立、不互相通信**——跨角度互证不在此发生，下沉到 Phase 4 由 lead 合并完成。
2. 每个 `Agent` prompt 自包含：负责角度、覆盖组件、产出文件路径、`references/finding-schema.md` 的统一 schema。
3. 各分析师读对应 skill（analyze-architecture/-quality/-security/-integration/-performance/-experience），证据落 `file:line`，写 `_workspace/{angle}_findings.md`，返回时附 top-3 摘要给 lead。
4. `integration-analyst`/`experience-analyst` 的「背后接通没」这类跨角度疑问**记进各自 findings 的 cross_refs/notes**，由 lead 在 Phase 4 交叉合并时核对，**不假定运行中能问别人**。

## Phase 3：对抗式验证（轻量档跳过；按 finding 并行）
- 对每条 severity ∈ {critical,high,medium} 的 finding（low/info 跳过），**以反驳为默认假设**复核。
- **并行化**：验证天然无状态、各 finding 互不依赖 → lead **并行 fan-out 多个 `finding-verifier` 实例**（每实例读 `adversarial-verification` skill），按角度或按 finding 批分配，避免单 verifier 串行所有条目成为长尾。**DooD 类**（要 docker/testcontainers、有资源争用）**单独排队串行**，静态类全并行。
- 标准档：每条 1 票静态。深度档：3 票/多 lens + 对"可动态验证"项（测试缺口/契约错位/构建）走 DooD（`adversarial-verification/references/dood-recipe.md`）。
- 回写 `verification:{verdict,method,votes,note}` → `_workspace/{angle}_findings.verified.md`。
- DooD 不可用 → 回退静态、标 unverified-dynamic（报告"覆盖与局限"列出）。

## Phase 4：交叉合并 + 综合（lead 亲做）
这是把 fan-out 各自独立的产物收口的关键阶段，也是**跨角度互证的真正发生地**（替代 v2 想靠 SendMessage 做的事）。读齐六份 verified findings：
1. **只入正文 confirmed**；`refuted` 移入报告附录（留出处 + 反驳理由）；`unverified` 标疑列出。
2. **跨角度互证 + 去重合并** — 同根因多角度命中 → 合一条，cross_refs 互链，各角度视角并入「为何重要」；分析师在 Phase 2 记下的「背后接通没」这类跨角度疑问，在此由 lead 对照别的角度产物核对；存疑且关键的点，lead 发起**第二轮定向 `Agent`** 补证。
3. **裁决冲突** — 分析师/验证者判断相左 → 并列 + lead 裁决理由，不删任一方。
4. **统一定级 + 排序** — 全局视角重排 severity（含 UX 的用户影响口径），给修复优先级序列。
5. **写报告** → `_workspace/00_analysis_report.md`（结构见 `references/report-template.md`，含验证列 + refuted 附录 + 6 角度评分）。「覆盖与局限」一节**必须记录本次实际执行的拓扑 DAG**（哪些并行、哪些串行、验证用了几个 verifier 实例、哪些走了 DooD），让决策者看到真实调度而非假定。

### Phase 4b：验证跑模式（仅当本跑是「对照已知真值打分」时）
当用户把本跑当作**团队验证 / 回归打分**（给了一份已知问题清单、要量化精度与召回）时，lead 在打分前**必须逐条重核真值的「当前存活状态」**，不得拿陈旧清单直接对分：
- **真值会过期**：清单可能基于历史审计，期间问题已被修复。打分前 lead 亲自 grep/读码确认每条真值在当前 commit 上**是否仍存活**。已修复项 → 团队**不报它才是对的**，报了反而是假阳性；据此重算召回，避免被陈旧清单污染（2026-06-25 跑：GT-4 API-001 已修复、GT-1 目标 service 未接线，均靠此校准）。
- **精度陷阱**：把「已修复 / 设计正确」的旧问题（如单向哈希被误读成可逆加密、denyAll 占位、必要的 CSRF 豁免、test pepper）列为**预期不报项**；团队若报 → 计假阳性。
- 记分卡分三栏：**召回**（命中存活真值）/ **精度**（是否制造 FP）/ **净增价值**（真值之外新发现的、经复核 confirmed 的真问题）。三者齐报才完整。

## 统一 Finding Schema（摘要；完整见 `references/finding-schema.md`）
```yaml
- id: <ANGLE>-NN          # ARC-/QLT-/SEC-/INT-/PRF-/UX-
  angle: architecture|quality|security|integration|performance|experience
  component: backend|frontend|ai|db|infra|cross
  severity: critical|high|medium|low|info
  title: <一句话>
  location: <path:line>   # integration 给两侧; experience 可给路由/页面
  evidence: <最小代码片段或命令输出>
  description: <是什么 + 为何重要>
  recommendation: <可操作的修复方向>
  confidence: high|medium|low
  cross_refs: [<其他 finding id>]
  verification: {verdict: confirmed|refuted|unverified, method: static|dood, votes, note}  # verifier 回写
```

## 数据传递协议
- **返回值级**（`Agent` 返回）：各分析师/验证者把 top 摘要 + 产出文件路径返回给 lead，lead 据此收集。
- **续聊级**（`SendMessage`）：仅用于 lead 对某个**已 spawn**的子代理追加澄清/补证（非并发 agent 间实时互通——那不存在）。
- **文件级**（`_workspace/`）：`{angle}_findings.md` → `{angle}_findings.verified.md` → `00_analysis_report.md`；中间产出保留，仅最终报告输出到用户指定路径。

## 错误处理
- 分析师/验证者 1 次重试仍失败 → 不阻塞，报告标注该角度/该项缺失，用其余角度成文。
- 工具缺失 → 转静态并降 confidence；DooD 回退标 unverified-dynamic；"覆盖与局限"如实列出。**环境事实（2026-06-29）**：本机 **node 已在 PATH**（前端 lint/build 可本地跑，docker 仅回退）；**Java 仍无** → 后端 testcontainers 走 DooD 容器。
- 冲突数据 → 并列保留 + 出处，绝不单方删除。

## 测试场景
- **正常流（标准档）**：六角度并行 fan-out → experience 在 findings 记下"家长看不到通知历史，疑前端未接线"（cross_ref 指向 integration）→ integration 独立记下"后端 GET /notifications 就绪但前端无 api 文件" → lead 在 Phase 4 交叉合并发现二者同根，合为一条多角度佐证的 high，并行 verifier 静态复核 confirmed。
- **误报压制流**：security 报"seed 含弱 pepper 哈希" → verifier 静态复核发现是 test pepper 且不进生产路径 → verdict=refuted，移入附录附理由 → 不污染主报告。
- **降级流（深度档无 docker）**：verifier 欲 DooD 实跑验证"tenant 隔离测试缺口" → docker 不可用 → 回退静态、标 unverified-dynamic → 报告"覆盖与局限"列出需人工实跑确认。
- **轻量档**：六 sub-agent fan-out，无验证，产出快版评分 + 明确标注"未经验证"的 findings。
- **后续流**：用户「只重看 security 并补密钥管理」→ Phase 0 部分再执行（先做时效判定）→ 仅重启 security-analyst 读旧 findings 增量 → verifier 仅补验新条 → lead 仅更新报告安全节。
