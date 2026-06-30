> ⚠️ **历史档案（2026-06-29 部分退役）**：本文记录 2026-06-26 的原始设计。其中 **Tier-1 真人体验官（release-visual-validator agent + release-visual-acceptance skill）已退役**（盲探索又慢又高假阴险、当前不可执行；详见 OpenSpec change `fix-analysis-harness-design-flaws`）。**Tier-2 确定性 Playwright（`e2e/` + release.yml 硬门禁）仍在用**，并已吸收原 Tier-1 的可发现性/无死胡同断言。本文仅作历史保留，勿据 Tier-1 部分行动。

# 发版前双层验收设计（Release Visual Acceptance）

- **日期**：2026-06-26
- **状态**：设计已批准，待转 implementation plan
- **作者**：brainstorm（用户 + Claude）

## 1. 背景与动机

现有 8 个 sub-agent 组成 `component-analysis-orchestrator` 团队，其中 `experience-analyst`
是唯一的用户视角角色，但它**纯静态**——读前端路由/组件/spec 推断「功能兑现没」，charter
明确「脱离代码做判断、不实跑」。它不会真在浏览器里点、不截图、不看渲染结果。

发版（`v*` tag → `release.yml`）当前只有**浅冒烟**：db healthy + backend 进程在 + 前端返回
HTTP 200。没有任何真实用户流程验证。2026-06-26 的登录密码漂移 bug（种子 hash 是 `admin`
而文档/前端/specs 都写 `admin123`，导致所有 demo 账号登录 401）就是这种缺口的典型——
冒烟全绿但真人一个都登不进去。

**目标**：在发版前引入两道互补的关卡——一道由 Claude 代入真实用户**体验**界面是否合理，
一道由确定性脚本**功能**把关——共同提升发版质量。

## 2. 核心决策（brainstorm 结论）

| # | 决策 | 取值 |
|---|------|------|
| 1 | 集成形态 | **两层**：本地 agent 视觉探索 + CI 确定性脚本门禁 |
| 2 | 两层关系 | **彻底解耦**：CI 脚本基于代码理解独立编写，**不**由视觉 agent 生成 |
| 3 | 能力放置 | **新建独立 agent + skill**；`experience-analyst` 保持纯静态 charter 不动 |
| 4 | Tier-1 驱动 | **Playwright MCP** 交互驱动（每步快照+截图→按所见动作→再看） |
| 5 | Tier-1 性质 | **真人体验官**：无全局视角、不预知操作、不碰实现，只判「以人物设定办得顺不顺、有无反人类」 |
| 6 | Tier-2 驱动 | **确定性 Playwright**，基于契约/代码理解编写，CI **硬卡** |
| 7 | v1 范围 | 4 角色登录 + 旗舰闭环 |
| 8 | 职责划分 | 闭环**因果**归 Tier-2；Tier-1 体验官用 **seed 既有数据**纯探索（真人不做造数） |
| 9 | `e2e/` 位置 | **仓库根** `e2e/` |

## 3. 架构

```
本地(发版前)                          CI(打 tag 后)
┌─────────────────────────────┐      ┌──────────────────────────────┐
│ Tier-1 真人体验官            │      │ Tier-2 功能门禁(硬卡)        │
│ Claude + Playwright MCP      │      │ 确定性 Playwright,基于代码    │
│ 代入人物·无全局视角·摸索     │      │ 编写·与 Tier-1 无关           │
│ 判:顺不顺/反不反人类         │      │ 判:功能对不对                 │
│ 出 GO/NO-GO + 体验反馈       │      │ 断言失败→镜像不发布           │
└─────────────────────────────┘      └──────────────────────────────┘
        独立·互不喂数据,只是发版前后两道关
```

新增物（`experience-analyst` 不动）：

| 新增物 | 路径 | 服务于 |
|--------|------|--------|
| Agent `release-visual-validator`（opus） | `.claude/agents/release-visual-validator.md` | Tier-1 |
| Skill `release-visual-acceptance` | `.claude/skills/release-visual-acceptance/SKILL.md` | Tier-1 |
| Playwright MCP server 配置 | `.mcp.json` | Tier-1 |
| 确定性 E2E 套件 | `e2e/playwright.config.ts`、`e2e/package.json`、`e2e/release-acceptance.spec.ts` | Tier-2 |
| CI 门禁步骤 | `.github/workflows/release.yml`（`build-smoke` 内新增步骤） | Tier-2 |

## 4. Tier-1：真人体验官（本地）

### 4.1 行为契约

每次运行**代入一个人物**。agent 只拿到四样东西：

1. **我是谁**（人物设定，如「一位家长，孩子在这家幼儿园」）
2. **我想办成什么事**（目标，如「看看孩子今天在园里有没有异常告警、有没有相关通知」）
3. **我自己的账号**（人物自己的登录凭据——真人知道自己的账号密码）
4. **入口 URL**（`http://localhost:80`）

**不给** UI 地图、不给操作步骤、不给任何代码/架构/实现信息。

### 4.2 驱动机制

通过 **Playwright MCP**：每一步拿到当前页面的快照（accessibility 树）+ 截图 →
agent **像第一次使用的人「看到什么才动什么」** → 选择点击/输入 → 再拿下一步快照+截图。
浏览器状态跨步保持，实现真正的**无预知探索**。

### 4.3 判断口径

只回答一件事：**以这个人物设定，我的事办顺了吗？哪里反人类 / 看不懂 / 走进死胡同？**
完全不评判实现（背后接没接通、代码好不好都不关心）。

### 4.4 反馈分级路由

| 类型 | 定义 | 处置 |
|------|------|------|
| **恶性缺陷** | 挡住人物办成核心任务 / 界面烂到不可用 | **NO-GO，挡发版** → 修复 → 重走整条发版前流程，再到此关 |
| **体验小建议** | papercut、可优化但不致命 | 反馈到 **Main Session**，**不挡发版** |

### 4.5 v1 人物集

| 人物 | 账号 | 目标（只给目标，不给步骤） |
|------|------|---------------------------|
| 家长 | `guardian-kg1` / `admin123` | 登录后想知道孩子今天在园里有没有异常/告警，并查看相关通知 |
| 教师 | `teacher-kg1` / `admin123` | 想查看本班的检测事件，并对其中一个做复核确认 |
| 园长 | `director-kg1` / `admin123` | 想了解本园概况、看待审批/管理项 |
| 超管 | `admin` / `admin123` | 想跨园查看平台层面的概况 |

> 人物探索基于 `db/initdb/` 的 **seed 既有数据**（已有 detection_events / event_reviews /
> notifications seed），真实用户不会自己注入事件。

### 4.6 产出

- 截图：`_workspace/visual-acceptance/<run>/`（gitignored）
- 报告：逐人物叙事（我想干啥 → 实际遇到啥 → 卡在哪/顺不顺）+ 截图引用 + `UX-` 前缀
  findings（沿用 `component-analysis-orchestrator` 的 finding schema）+ 一句 **GO / NO-GO**
- 恶性缺陷 → NO-GO 阻断；小建议 → 汇报 Main Session

## 5. Tier-2：功能门禁（CI，硬卡）

### 5.1 编写来源

`e2e/release-acceptance.spec.ts` 从**契约/代码理解**编写确定性断言（选择器、API、
预期结果）。**与 Tier-1 完全无关**——不消费视觉 agent 的任何产出。

### 5.2 覆盖（v1）

- **4 角色登录**：4 个 demo 账号各自登录成功并落到对应首页（无错误态）。
- **旗舰闭环因果**：经内部 ingest 端点注入检测事件（带 CI 环境的 `AI_SERVICE_TOKEN`）→
  teacher 看板出现该事件 → 复核确认 → guardian **站内收件箱**出现对应通知。

> **边界**：闭环终点取**站内通知**（INT-01 收件箱，浏览器可见可断言）。外部 Pushover/SMS
> **不在门禁内**——CI 无真实投递凭据，也不应真发。写进 skill / 测试注释的「覆盖与局限」。

### 5.3 CI 集成

在 `release.yml` 的 `build-smoke` job 中、现有浅冒烟（前端 200）**之后**新增：

1. 栈已 up（job 已起栈）→ `npx playwright test`（跑 `e2e/release-acceptance.spec.ts`）。
2. 截图全部 `actions/upload-artifact`（失败时人工回看）。
3. **任一断言失败 → 步骤非零退出 → job 失败 → `v*` 镜像不 push**（硬门禁，卡在 push
   `:version` 之前）。

CI 无 Claude：此层纯确定性脚本，不做视觉推理。

### 5.4 防抖

`playwright.config.ts` 设 `retries: 1`、显式 `expect` 等待，避免偶发 flake 误卡发版。

## 6. 流水线位置

```
发版前(本地):
  跑 Tier-1 体验官 + 既有测试套件
    ├─ 恶性缺陷 → NO-GO → 修复 → 回到本步重走
    └─ GO → 打 v* tag
          ↓
CI(release.yml on tag):
  build-smoke: 起栈 → 浅冒烟 → Tier-2 Playwright(硬卡) → push :version
          ↓
  人工审批 → deploy-prod 提升 :prod → watchtower 部署
```

- Tier-1 = 本地、发版前、人工触发（也可被 orchestrator 深度档「发版前」作为子环节调用，
  可选不强绑）。
- Tier-2 = CI、打 tag 后、自动硬卡。

## 7. 杂项

- Agent 模型：**opus**（视觉推理 + 自适应探索）。
- `.gitignore` 追加 `_workspace/visual-acceptance/`（本地产物）；CI 截图走 artifact 不入库。
- `.mcp.json` 新增 Playwright MCP server（Tier-1 用；npx 或 docker 启动方式在 plan 阶段定）。
- `CLAUDE.md` 变更历史表落一行（第 9 个 agent + 新 skill + 双层门禁）。

## 8. 覆盖与局限

- Tier-1 是**人物主观判断**，非确定性——同一版本两次跑结论可能略有出入；它的价值在
  「抓反人类设计」，不替代 Tier-2 的功能正确性。
- 外部 PUSH/SMS 投递不在任一层验证范围（无真实凭据）。
- v1 只覆盖 4 角色登录 + 旗舰闭环；其余角色任务（家长绑娃/看感谢信、教师发公告、园长
  管摄像头等）留待后续迭代扩 persona 集与 Tier-2 用例。

## 9. 后续（非本设计范围）

- 扩 persona 集与 Tier-2 用例覆盖更多角色任务。
- Tier-1 报告可考虑接入 orchestrator 的统一报告模板。
- 外部投递通道的端到端验证（需 sandbox 投递凭据）。
