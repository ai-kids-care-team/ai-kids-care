## ADDED Requirements

### Requirement: 编排器执行模型不得依赖本环境不存在的工具

`component-analysis-orchestrator` skill 与 `analysis-lead` agent 描述的执行模型 MUST 仅使用本运行环境实际可用的编排原语（`Agent`、`SendMessage`、`Task*`）。编排器 MUST NOT 声明或依赖 `TeamCreate` 或任何「并发运行的 agent 之间实时互发消息」的能力，因为该能力在本环境不存在，会导致标准/深度档静默降级。

#### Scenario: 编排器文本不再引用不存在的团队原语
- **WHEN** 审阅 `component-analysis-orchestrator/SKILL.md` 与 `analysis-lead.md`
- **THEN** 不出现 `TeamCreate` 调用，也不出现「真团队/实时互证/边做边互证」等暗示并发 agent 互通的措辞
- **AND** 标准/深度档与轻量档的差异由「验证强度」「是否 DooD」等真实可执行的维度定义，而非「是否组团队」

#### Scenario: 跨角度互证下沉为显式合并阶段
- **WHEN** integration-analyst 或 experience-analyst 需要别的角度的结论来佐证一条边界 finding
- **THEN** 该互证由 lead 在收齐所有分析师产物后的「交叉合并阶段」完成，或由 lead 对存疑点发起第二轮定向 `Agent` 调用
- **AND** 不假定分析师在运行中能彼此通信

### Requirement: 编排器 SHALL 按任务依赖图选择执行拓扑

编排器 MUST 提供一个与运行档位正交的「执行拓扑选择」步骤：依据本次任务清单及其依赖关系构建 DAG，对同层无依赖的节点一律并行 fan-out，仅在存在真实数据依赖处串行。pipeline（流式）MUST 仅出现在有前后数据依赖的环节，而非默认形态。

#### Scenario: 无依赖的多角度分析并行执行
- **WHEN** 一次分析涉及多个互不依赖的角度（如架构/质量/安全/集成/性能/体验）
- **THEN** 这些角度以并行 `Agent` fan-out 执行，而非逐个串行
- **AND** 仅 Explore→分析、分析→验证、验证→综合 这类硬依赖处保持串行

#### Scenario: 验证阶段按 finding 并行
- **WHEN** 进入 finding 验证阶段且存在多条待验证 finding
- **THEN** 静态类验证以多个无状态 verifier 实例并行执行
- **AND** 需要 DooD（docker/testcontainers）的验证单独排队串行，避免资源争用
- **AND** 最终报告的「覆盖与局限」一节记录本次实际采用的执行拓扑（DAG）

### Requirement: MCP 与 settings 配置 MUST 保持一致

工作树中被引用的 MCP server 配置 MUST 与启用声明一致：`.claude/settings.local.json` 的 `enabledMcpjsonServers` 不得 enable 一个在 `.mcp.json` 中不存在的 server。

#### Scenario: 不存在「enable 了不存在的 server」的半截状态
- **WHEN** 审阅 `.mcp.json` 与 `.claude/settings.local.json`
- **THEN** `enabledMcpjsonServers` 中每一项都能在 `.mcp.json` 的 `mcpServers` 找到对应定义；或两处一并移除该 server
- **AND** 不存在「enable 了 playwright 但 `.mcp.json` 为空」这类不一致

### Requirement: Harness 成文假设 MUST 与当前环境事实一致

Harness 资产中固化的环境假设 MUST 经当前事实核验后才保留。已知失真的假设 MUST 被校正：node 已在 PATH（「本机无 node」失效）、`frontend/` 未被 gitignore（Grep/Glob 对其可见）。

#### Scenario: 校正「frontend 被 gitignore」的错误警告
- **WHEN** 审阅 `adversarial-verification/SKILL.md`
- **THEN** 不再断言「frontend 整树被忽略、必须裸 rg --no-ignore」
- **AND** 若保留相关提醒，改为通用形式「复核存在性结论前先 `git check-ignore` 确认目标是否真被忽略」

#### Scenario: 校正「本机无 node」化石假设
- **WHEN** 审阅 harness/笔记中关于本地工具链的前提
- **THEN** 标注 node 已在 PATH、前端 lint/build 可直接本地跑、docker `node:20` 仅作回退
- **AND** 区分清楚「Claude settings hook 解释器仅 git/powershell」与「Bash 工具可见 node」是两件事

### Requirement: release-visual-validator MUST 退役且其价值无缺口下沉

维护者已决策退役 release-visual-validator（盲探索又慢又高假阴险、当前不可执行）。相关 agent 定义、`release-visual-acceptance` skill 与 playwright 启用 MUST 一并清理，且全仓 MUST 无悬挂引用。退役 MUST NOT 造成覆盖缺口：「功能是否接线」由 experience-analyst 静态覆盖、「功能正确性」由 Tier-2 `e2e/` 覆盖，原属真人体验官的可发现性断言 MUST 下沉为 `e2e/` 的确定性断言。

#### Scenario: 彻底清理退役资产
- **WHEN** 退役 release-visual-validator
- **THEN** 移除 `.claude/agents/release-visual-validator.md`、`.claude/skills/release-visual-acceptance/`，并清理 `settings.local.json`/`.mcp.json` 的 playwright 启用
- **AND** 全仓搜索 release-visual-validator / release-visual-acceptance 无悬挂引用（含 CLAUDE.md 与其它文档）

#### Scenario: 退役不留覆盖缺口
- **WHEN** 退役完成
- **THEN** 「关键 CTA 在 N 次点击内可达」「核心流程每页有返回/前进出路」作为确定性断言已加入 Tier-2 `e2e/`
- **AND** 「功能是否接线」由 experience-analyst 静态覆盖、「功能正确性」由 `e2e/` 覆盖

### Requirement: Harness 文档与产物状态 MUST 准确

`CLAUDE.md` 的 Harness 节 MUST 准确反映实际 agent 集合；`_workspace/` 中间产物 MUST 有时效判定避免误用陈旧结论；编排器触发词 MUST 与相邻 skill 区隔。

#### Scenario: 模型分配表覆盖全部 agent
- **WHEN** 审阅 `CLAUDE.md` 的模型分配口径
- **THEN** 9 个 agent 全部在册（含 release-visual-validator），或明确标注其不在分析编排表内

#### Scenario: _workspace 产物按时效判定
- **WHEN** 编排器 Phase 0 发现 `_workspace/` 已存在产物
- **THEN** 依据文件日期/commit 戳判定时效，超期或跨 commit 即提示作废，而非仅凭目录存在就复用

#### Scenario: 触发词与相邻工具区隔
- **WHEN** 用户请求「审计 harness/agent/skill 自身」
- **THEN** `component-analysis-orchestrator` 的 description 含排除性指引，引导改用 `harness:harness`；审计业务工程组件才用本 skill
