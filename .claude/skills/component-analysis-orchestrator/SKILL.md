---
name: component-analysis-orchestrator
description: 编排「组件多角度分析团队」对本工程做架构/安全/质量/集成四角度的并行审查并综合成报告。当用户要求"分析工程/组件"、"多角度分析"、"代码审查/健康度评估"、"架构/安全/质量/集成审查"，或要求"重新分析"、"再跑一遍"、"更新分析"、"只重看 X 组件"、"基于上次结果改进"时，必须使用本 skill。区别于 /code-review（仅看 diff）——本 skill 审查整个组件或工程的现状。
---

# 组件多角度分析编排器（Orchestrator）

把架构、安全、质量、集成四个角度的专家组成一个**自协调团队**，对 AI Kids Care 工程（backend / frontend / ai / db / infra）做多角度审查，最后由 lead 综合成一份决策者可读的报告。

**执行模式：agent 团队（默认）。** 四位分析师用 `TeamCreate` 组队，`SendMessage` 交叉确认，`TaskCreate` 跟踪依赖；lead（本编排器）监控并综合。
**模型分配**（基线 sonnet，重推理角色上 opus）：`security-analyst`、`integration-analyst` = **opus**（威胁建模 / 双侧契约交叉推理，假阳性代价高）；`architecture-analyst`、`quality-analyst` = **sonnet**（结构阅读 / 机械扫描为主）；`analysis-lead` = **opus**。Agent 工具调用时按此显式传 `model`。
**语言约定**：lead 对用户输出中文为主、英文为辅；团队内 `SendMessage` 可用英文以保语义精确；产出文件（findings / 报告）中文为主。

## Phase 0：上下文确认（先做）

判断本次是初次 / 后续 / 部分再执行：
- `_workspace/` 不存在 → **初次执行**：走完整 Phase 1–4。
- `_workspace/` 存在 + 用户要求局部修订/补充某角度 → **部分再执行**：只重启相关分析师，复用其余 `*_findings.md`。
- `_workspace/` 存在 + 用户给了新范围/新输入 → **全新执行**：把旧 `_workspace/` 重命名为 `_workspace_prev/`，重头来。

并确认**范围**：默认全 5 组件 + 全 4 角度；用户若限定（如「只看 backend 安全」）则按其缩小。

## Phase 1：准备架构地图

分析师需要工程全貌才能高效定位。若团队上下文中**没有**现成架构地图：先派一个 `Explore` 子代理产出 `_workspace/architecture_map.md`（组件→技术栈/入口/关键目录/通信方式）。若已有则直接复用。

## Phase 2：组队与分派

1. `TeamCreate` 组建 `component-analysis-team`，成员：`architecture-analyst`（sonnet）、`security-analyst`（opus）、`quality-analyst`（sonnet）、`integration-analyst`（opus）。
2. `TaskCreate` 为每位分析师建任务，注明：负责角度、覆盖组件、产出文件路径、依赖关系。
   - `integration-analyst` 依赖其余三者的中途线索（边界问题汇聚点），但**不阻塞启动**——四者并行开工，integration 边做边接收 SendMessage。
3. 分派时附 `references/finding-schema.md` 的统一 schema 要求。

## Phase 3：并行分析 + 自协调

- 四位分析师各自读对应 skill（analyze-architecture / -security / -quality / -integration），按其镜头审查，证据落 `file:line`。
- **交叉确认**：任一分析师发现跨角度线索，`SendMessage` 给相关队友；边界相关一律抄送 `integration-analyst`。
- 各自把 finding 写入 `_workspace/{angle}_findings.md`（schema 见下），完成后 SendMessage 通知 lead + 附 top-3 摘要。

## Phase 4：综合（lead 亲做）

lead 读齐四份 findings：
1. **去重合并** — 同根因多角度命中 → 合一条，cross_refs 互链，各角度视角并入「为何重要」。
2. **裁决冲突** — 分析师判断相左 → 并列 + lead 裁决理由，不删任一方。
3. **统一定级 + 排序** — 全局视角重排 severity，给修复优先级序列。
4. **写报告** → `_workspace/00_analysis_report.md`（结构见 `references/report-template.md`）。

## 统一 Finding Schema（所有分析师必须遵守）

```yaml
- id: <ANGLE>-NN          # ARC- / SEC- / QLT- / INT-
  angle: architecture|security|quality|integration
  component: backend|frontend|ai|db|infra|cross
  severity: critical|high|medium|low|info
  title: <一句话>
  location: <path:line>   # integration 必须给两侧
  evidence: <最小代码片段或命令输出>
  description: <是什么 + 为何重要>
  recommendation: <可操作的修复方向>
  confidence: high|medium|low
  cross_refs: [<其他 finding id>]
```
> 完整字段说明与示例见 `references/finding-schema.md`。报告结构见 `references/report-template.md`。

## 数据传递协议
- **任务级**（TaskCreate/Update）：跟踪四角度进度与依赖。
- **消息级**（SendMessage）：分析师间实时交叉确认、lead 催证。
- **文件级**（`_workspace/`）：`{phase}_{agent}_{artifact}` 命名；中间产出保留，仅最终报告输出到用户指定路径。

## 错误处理
- 分析师 1 次重试后仍失败 → 不阻塞团队，报告标注该角度缺失，用其余三角度成文。
- 工具缺失（本机无 node/java，testcontainers 需 DooD）→ 分析师转静态评估并降 confidence，报告「覆盖与局限」节如实列出。
- 冲突数据 → 并列保留 + 出处，绝不单方删除。

## 测试场景
- **正常流**：用户「多角度分析当前工程」→ Phase 0 判定初次 → 备好架构地图 → 四分析师并行 → integration 收到 2 条边界抄送并验证 → lead 去重得 ~20 findings → 报告含 5 组件健康度评分 + 优先级路线图。
- **错误流**：本机无 Java，quality-analyst 跑不了 testcontainers → 转为静态评估测试断言质量、标「未实际执行」→ lead 在「覆盖与局限」注明 backend 测试未动态验证 → 报告仍完整交付。
- **后续流**：用户「只重看 security 角度并补充密钥管理」→ Phase 0 判定部分再执行 → 仅重启 security-analyst 读旧 findings 增量 → lead 仅更新报告安全节。
