## 1. 零风险止血（配置 + 化石假设 + 文档）

- [x] 1.1 决定 playwright 去留方向（已决策：退役 → 删除）
- [x] 1.2 收口 `.mcp.json` ↔ `.claude/settings.local.json` 一致性：`enabledMcpjsonServers` 清空（`.mcp.json` 保持空，退役方向）
- [x] 1.3 校正 `adversarial-verification/SKILL.md` 的「frontend 被 gitignore」警告：改写为「先 `git check-ignore` 确认再决定」+ 明确 frontend 未被忽略
- [x] 1.4 校正「本机无 node」化石假设：dood-recipe 标注 node 已在 PATH、本地优先 docker 回退；区分 hook 解释器约束 vs Bash 工具可见 node
- [x] 1.5 修补 `CLAUDE.md`：模型表本就只列 8 个分析 agent（validator 退役后准确）；Harness 变更历史追加本次（注明「刻意不归档」）

## 2. 本地 pre-push 预检

- [x] 2.1 新增 `.githooks/pre-push`（POSIX sh）：检测 `frontend/` 改动→跑 ESLint，失败非零退出阻止推送；只跑 lint 不跑 build
- [x] 2.2 node 健壮回退：本地用 `node node_modules/eslint/bin/eslint.js`（绕开 .bin shim 平台差异），无 node 回退 `node:20` 容器
- [x] 2.3 启用分发：已 `git config core.hooksPath .githooks` + hook 置可执行位（100755）+ `.githooks/README.md` 注明新克隆需执行一次
- [x] 2.4 验证通过：注入 parse-error→hook 拦截(exit 1)；干净前端改动→放行(exit 0)；无前端改动→静默跳过
- [~] 2.5（可选，从略）PowerShell `Stop` hook 早期提醒——按「最小变更」原则未做；git pre-push 已是真正的门，Stop hook 仅锦上添花，留作后续可选

## 3. release-visual-validator 退役（已决策）

- [x] 3.1 删除 `.claude/agents/release-visual-validator.md`（git rm）
- [x] 3.2 删除 `.claude/skills/release-visual-acceptance/SKILL.md`（git rm）
- [x] 3.3 清理 playwright 启用：`settings.local.json` 清空 `enabledMcpjsonServers`、`.mcp.json` 保持空
- [x] 3.4 新增 `e2e/tests/discoverability.spec.ts`：可发现性（核心功能≤1 点击可达）+ 无死胡同（核心页保留导航出口）确定性断言
- [x] 3.5 清除悬挂引用：CLAUDE.md 记退役；两份 docs/superpowers 历史文档加「已退役」横幅（保留历史不删内容）
- [x] 3.6 验证：无活动悬挂引用；`e2e/` 新 3 用例被 Playwright 正确注册解析（实跑由 release.yml 硬门禁负责）

## 4. 编排器重写：诚实 fan-out DAG + 拓扑路由 + 验证并行

- [x] 4.1 删除 SKILL.md 与 analysis-lead.md 的 `TeamCreate` 调用及「真团队/实时互证」措辞（仅留解释为何不存在的文字）
- [x] 4.2 改写为三阶段：Explore → `Agent` 并行 fan-out → Phase 4 lead 交叉合并（跨角度互证 + 去重定级 + 第二轮定向 `Agent`）
- [x] 4.3 新增 Phase 1.5「执行拓扑选择」（与档位正交）：依赖图驱动并行/串行，标准 DAG 写明，pipeline 仅在真依赖处
- [x] 4.4 Phase 3 验证并行化：并行 fan-out 多 verifier 实例（静态全并行），DooD 类单独排队串行
- [x] 4.5 三档差异回归「验证强度 / 是否 DooD」（Phase 0.5 表重写）；Phase 4 报告「覆盖与局限」必记实际 DAG
- [x] 4.6 验证：grep 确认无活动 `TeamCreate` 残留；结构（fan-out DAG / 拓扑路由 / 验证并行）已就位。注：标准档**实跑**留待下一次真实分析时behavioral 确认（不为验证文档而额外发起整队 fan-out）

## 5. 状态机与触发边界清理

- [x] 5.1 Phase 0 增加 `_workspace/` 时效判定（读日期/commit 戳，跨 commit 标失效需重核，不再仅凭目录存在复用）
- [x] 5.2 `.gitignore` 覆盖整个 `_workspace/`（注明长期报告应落 `docs/assessments/`）
- [x] 5.3 收窄 description 触发词 + 排除性指引（审计 harness/agent/skill 自身→`harness:harness`；只看 diff→`/code-review`）

## 6. 收口验证

- [x] 6.1 逐条核对 specs Scenario：编排器无 TeamCreate ✓ / 拓扑路由 ✓ / 验证并行 ✓ / 配置一致 ✓ / 化石假设校正 ✓ / validator 退役无缺口 ✓ / 文档准确 ✓ / pre-push 三路径 ✓
- [x] 6.2 `openspec validate fix-analysis-harness-design-flaws --strict` → valid
- [x] 6.3 git status 确认改动仅在 harness/配置/文档/e2e 测试，未触碰 backend/frontend/ai/db 业务代码与业务 spec；变更刻意不归档（CLAUDE.md + design + 本文件已注明）
