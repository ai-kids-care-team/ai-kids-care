## Context

「组件多角度分析」harness（9 个 agent + 9 个 skill + 编排器）与「发版前视觉验收」（Tier-1 真人体验官 + Tier-2 `e2e/`）是自建元基础设施。2026-06-29 一次隔离 sub-agent 审计核实出一组实锤问题，共同病根是**成文设计透支了实际工具与环境能力**：

- 编排器调用本环境不存在的 `TeamCreate`，标准/深度档命中「团队不可用→降级 fan-out」，三档差异退化。
- release-visual-validator「无预知盲探索」必然又慢又找不到入口；且 `.mcp.json` 已被改空、`settings.local.json` 仍 enable playwright，当前跑不起来。
- 无任何本地提交前/推送前校验，lint 必到 CI 才暴露。
- 多条固化假设已失真：node v24 实际在 PATH、`frontend/` 实际未被 gitignore。
- CLAUDE.md 模型表漏第 9 个 agent；`_workspace/` 状态机靠「目录在不在」判定易误用陈旧产物；触发词过宽与 `harness:harness` 双命中。

本变更只动 harness 资产、配置与工程纪律文件，不动业务代码，且**实现后不归档**（不并入 `openspec/specs/` 业务能力）。

## Goals / Non-Goals

**Goals:**
- 把编排器改写成「能真跑」的诚实 fan-out DAG，并引入与档位正交的执行拓扑路由 + 验证并行。
- 让 release-visual-validator 要么可执行且非阻塞、要么干净退役；其确定性价值下沉 Tier-2 `e2e/`。
- 建立本机约束下可落地、随仓库分发的本地 pre-push lint 预检。
- 校正所有已失真的成文假设，修补文档与配置一致性。

**Non-Goals:**
- 不新增分析角度，不扩张 agent 数量。
- 不改动任何 `backend/`/`frontend/`/`ai/`/`db/` 业务代码与业务 spec。
- 不把本变更并入主 specs（实现后保留在 `changes/` 不归档）。
- 不在 pre-push 引入 build 等重检查（留给 CI）。

## Decisions

### D1. 编排器：fan-out DAG + 显式合并，删除 TeamCreate 语义
删除 `TeamCreate`/「实时互证」措辞。三阶段：①Explore 出架构地图 → ②`Agent` 并行 fan-out 六分析师（彼此独立）→ ③lead「交叉合并」阶段做去重定级 + 对存疑点发起第二轮定向 `Agent`。原本想靠 SendMessage 做的 integration↔experience 互证下沉为 lead 的合并子步骤。三档差异回归到「验证强度 / 是否 DooD」这类真实维度。

### D2. 执行拓扑路由（与档位正交）
新增 Phase「拓扑选择」：输入任务清单 + 角度集 + 依赖关系，产出显式 DAG。调度规则：同层无依赖节点并行 fan-out，跨层串行，pipeline 只在真有数据依赖处出现。验证阶段按 finding 并行多 verifier（静态类全并行），DooD 类单独串行排队。报告「覆盖与局限」记录本次实际 DAG。

### D3. release-visual-validator——已决策：退役
维护者 2026-06-29 拍板**退役**该角色（不采用方案 C）。理由：盲探索又慢又高假阴险、当前还跑不起来，其价值可被静态 + 确定性两层完整覆盖。落地：删除 release-visual-validator agent 定义 + `release-visual-acceptance` skill + 清理 playwright 启用（与 D5 一并收口）。退役后职责再分配——「功能是否接线」由 experience-analyst 静态覆盖、「功能正确性」由 Tier-2 `e2e/` 覆盖；并把「关键 CTA N 次点击内可达」「核心流程无死胡同」这两类原属真人体验官的可发现性断言下沉为 `e2e/` 的确定性断言，避免退役造成覆盖缺口。

### D4. 本地预检——版本化 .githooks/pre-push
`.githooks/pre-push`（POSIX sh）+ `git config core.hooksPath .githooks` 随仓库分发。逻辑：检测推送是否含 `frontend/` 改动→有则 `cd frontend && npx eslint`；先探 `command -v node`，缺失回退 `docker run --rm node:20`。只跑 lint 不跑 build。可选：`.claude/settings.json` 加一个 PowerShell `Stop` hook 在会话结束对改过的前端文件做 lint 提醒（符合「解释器仅 git/powershell」约束），但 git pre-push 才是真正的门。

### D5. 配置一致性收口
`.mcp.json` ↔ `settings.local.json` 二选一收口：保留 playwright 则把 server 定义写回 `.mcp.json`（固定镜像 tag）；退役则两处一并删。不留半截。

### D6. 化石假设校正与文档修补
- `adversarial-verification/SKILL.md`：删/改「frontend 被 gitignore」错误警告为「先 `git check-ignore` 再决定」。
- node 假设：在 spike 笔记/dood-recipe/CLAUDE.md 标注 node 已在 PATH、docker 仅回退；区分 hook 解释器约束 vs Bash 工具可见 node。
- `CLAUDE.md`：模型表补第 9 个 agent；变更历史追加本次。
- `_workspace/`：Phase 0 增加时效判定（读日期/commit 戳）；`.gitignore` 覆盖整个 `_workspace/`。
- 收窄 `component-analysis-orchestrator` 触发词，加与 `harness:harness`/`/code-review` 的排除性指引。

## Risks / Trade-offs

- **编排器重写改变既有行为（BREAKING）**：旧「团队」措辞被删。缓解——语义其实从未真正生效（一直在降级 fan-out），改写只是让文本与实际一致，运行时风险低。
- **方案 C vs 退役的取舍**：C 保留「初见可发现性」探测但仍有不确定与成本；退役更省但失去该探测。决策放到 apply 时由维护者拍板，spec 两条 scenario 都已覆盖。
- **pre-push 增加推送耗时**：仅前端改动时触发、只跑 lint，影响可控；docker 回退路径较慢但仅在无 node 时走。
- **core.hooksPath 是本地 git 配置**：需每个克隆执行一次（或在 README/onboarding 注明）；不能强制，属软约束。
- **不归档的长期影响**：delta spec 永久留在 `changes/`，与主 specs 不同步是有意为之；需在变更历史注明「harness 维护、刻意不归档」以免后人误归档。
