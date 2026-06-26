---
name: component-analysis-orchestrator
description: 编排「组件多角度分析团队」对本工程做架构/质量/安全/集成/性能/用户六角度的并行审查、对抗式验证并综合成报告。当用户要求"分析工程/组件"、"多角度分析"、"代码审查/健康度评估"、"架构/安全/质量/集成/性能/用户体验审查"，或"重新分析/再跑一遍/更新分析/只重看 X 组件/基于上次结果改进"、或指定"快扫/快速体检"(轻量)、"深度/发版前/关键审计"(深度)时，必须使用本 skill。区别于 /code-review（仅看 diff）——本 skill 审查整个组件或工程的现状。
---

# 组件多角度分析编排器（Orchestrator v2）

把六个角度的专家组成一个**自协调团队**，对 AI Kids Care 工程（backend / frontend / ai / db / infra）做多角度审查，经**对抗式验证**压制假阳性后，由 lead 综合成一份决策者可读的报告。

## 角色与模型分配（Agent 调用时按此显式传 `model`）
| 角色 | 模型 | 立场 |
|------|------|------|
| architecture-analyst | sonnet | 纯结构：分层/依赖/耦合/模式 |
| quality-analyst | sonnet | 可维护性/复杂度/重复/测试质量 |
| security-analyst | opus | 认证授权/多租户/PII·密钥/注入/审计 |
| integration-analyst | opus | 跨组件契约双侧交叉比对 |
| performance-analyst | opus | 扩展性/性能：N+1/事务内 IO/线程池/背压/多实例 |
| experience-analyst | opus | 用户/功能视角（唯一由外向内） |
| finding-verifier | opus | 对抗式复核（静态 + 深度档 DooD） |
| analysis-lead | opus | 选档/组队/监控/去重裁决定级/综合 |

**语言约定**：lead 对用户输出中文为主、英文为辅；团队内 `SendMessage` 可用英文以保语义精确；产出文件（findings / 报告）中文为主。

## Phase 0：上下文确认（先做）
判断初次 / 后续 / 部分再执行：
- `_workspace/` 不存在 → **初次执行**：走完整 Phase 1–4。
- `_workspace/` 存在 + 用户要求局部修订/补充某角度 → **部分再执行**：只重启相关分析师，复用其余 `*_findings.md`。
- `_workspace/` 存在 + 用户给了新范围/新输入 → **全新执行**：旧 `_workspace/` 改名 `_workspace_prev/`，重头来。

确认**范围**：默认全 5 组件 + 全 6 角度；用户限定（如「只看 backend 安全」）则缩小。

## Phase 0.5：选档（决定执行模式与验证强度）
用户显式指定 > 触发词 > 默认**标准**。

| 档 | 角度 | 执行模式 | 验证 |
|---|---|---|---|
| **轻量 light**（"快扫/快速体检"） | 6 | sub-agent fan-out（无团队开销） | 无 |
| **标准 standard**（默认） | 6 | 真 agent team（TeamCreate + SendMessage 实时互证） | finding-verifier 对每条 **high+medium** 静态复核 1 次 |
| **深度 deep**（"深度/发版前/关键审计"） | 6 | 真 agent team | 静态升级 3 票/多 lens + **DooD** 对可动态验证项实跑坐实 |

## Phase 1：准备架构地图
若团队上下文无现成架构地图：派 `Explore` 子代理产出 `_workspace/architecture_map.md`（组件→技术栈/入口/关键目录/通信方式）。已有则复用。

## Phase 2：分析（执行模式随档）
**轻量档**：用 `Agent` 工具并行 fan-out 六个分析师（`run_in_background`），各按上表模型，结果回 lead。无团队、无 SendMessage。
**标准/深度档**：
1. `TeamCreate` 组建 `component-analysis-team`，成员为六位分析师（模型见上表）。
2. `TaskCreate` 为每位建任务：负责角度、覆盖组件、产出文件路径、依赖。
   - `integration-analyst` 是边界问题汇聚点、`experience-analyst` 常需问"背后接通没"，二者**不阻塞启动**——六者并行开工，边做边经 SendMessage 互证。
3. 分派附 `references/finding-schema.md` 的统一 schema。

各分析师读对应 skill（analyze-architecture/-quality/-security/-integration/-performance/-experience），证据落 `file:line`，写 `_workspace/{angle}_findings.md`，完成 SendMessage 通知 lead + top-3。

## Phase 3：对抗式验证（轻量档跳过）
- `finding-verifier`（读 `adversarial-verification` skill）对每条 severity ∈ {critical,high,medium} 的 finding **以反驳为默认假设**复核（low/info 跳过）。
- 标准档：每条 1 票静态。深度档：3 票/多 lens + 对"可动态验证"项（测试缺口/契约错位/构建）走 DooD（`adversarial-verification/references/dood-recipe.md`）。
- 回写 `verification:{verdict,method,votes,note}` → `_workspace/{angle}_findings.verified.md`。
- DooD 不可用 → 回退静态、标 unverified-dynamic（报告"覆盖与局限"列出）。

## Phase 4：综合（lead 亲做）
读齐六份 verified findings：
1. **只入正文 confirmed**；`refuted` 移入报告附录（留出处 + 反驳理由）；`unverified` 标疑列出。
2. **去重合并** — 同根因多角度命中 → 合一条，cross_refs 互链，各角度视角并入「为何重要」。
3. **裁决冲突** — 分析师/验证者判断相左 → 并列 + lead 裁决理由，不删任一方。
4. **统一定级 + 排序** — 全局视角重排 severity（含 UX 的用户影响口径），给修复优先级序列。
5. **写报告** → `_workspace/00_analysis_report.md`（结构见 `references/report-template.md`，含验证列 + refuted 附录 + 6 角度评分）。

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
- **任务级**（TaskCreate/Update）：跟踪六角度 + 验证进度与依赖。
- **消息级**（SendMessage）：分析师间实时交叉确认、verifier 核对、lead 催证（可英文）。
- **文件级**（`_workspace/`）：`{angle}_findings.md` → `{angle}_findings.verified.md` → `00_analysis_report.md`；中间产出保留，仅最终报告输出到用户指定路径。

## 错误处理
- 分析师/验证者 1 次重试仍失败 → 不阻塞，报告标注该角度/该项缺失，用其余角度成文。
- 团队模式不可用（标准/深度档 TeamCreate 失败）→ **自动降级为 fan-out**（仍跑验证），报告注明降级。
- 工具缺失（本机无 node/java，testcontainers 需 DooD）→ 转静态并降 confidence；DooD 回退标 unverified-dynamic；"覆盖与局限"如实列出。
- 冲突数据 → 并列保留 + 出处，绝不单方删除。

## 测试场景
- **正常流（标准档）**：六角度组队并行 → experience 发现"家长看不到通知历史"，SendMessage 问 integration → integration 回"后端 GET /notifications 就绪但前端无 api 文件"（同根，cross_ref 互链）→ verifier 静态复核 confirmed → lead 合并为一条多角度佐证的 high。
- **误报压制流**：security 报"seed 含弱 pepper 哈希" → verifier 静态复核发现是 test pepper 且不进生产路径 → verdict=refuted，移入附录附理由 → 不污染主报告。
- **降级流（深度档无 docker）**：verifier 欲 DooD 实跑验证"tenant 隔离测试缺口" → docker 不可用 → 回退静态、标 unverified-dynamic → 报告"覆盖与局限"列出需人工实跑确认。
- **轻量档**：六 sub-agent fan-out，无团队无验证，产出快版评分 + 明确标注"未经验证"的 findings。
- **后续流**：用户「只重看 security 并补密钥管理」→ Phase 0 部分再执行 → 仅重启 security-analyst 读旧 findings 增量 → verifier 仅补验新条 → lead 仅更新报告安全节。
