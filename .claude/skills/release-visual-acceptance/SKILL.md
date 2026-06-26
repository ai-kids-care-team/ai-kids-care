---
name: release-visual-acceptance
description: 发版前视觉验收方法论——代入人物、无预知、用 Playwright MCP 真浏览器摸索、逐步截图、判反人类设计、按"恶性缺陷挡发版 / 小建议汇报 Main Session"分级路由。release-visual-validator 使用。当需要"发版前体验走查、真人视角验收、视觉探索、UX 可用性把关"时使用。
---

# release-visual-acceptance — 发版前真人体验验收方法

把 Claude 代入真实使用者，用真浏览器（Playwright MCP）摸索产品，判断「以这个人物，我的事
办得顺不顺、有没有反人类的设计」。这是发版前 Tier-1（本地、人工触发）；与 Tier-2（CI 确定性
Playwright 功能门禁）**完全解耦**——Tier-1 不写/不喂 CI 脚本，只产出体验结论。

## 为何这样验（原则）
确定性脚本能验"功能对不对"，但验不了"用起来反不反人类"。把 Claude 当一个**无预知的真人**
放进界面，能抓住脚本抓不到的东西：路径绕、文案看不懂、关键入口藏得深、流程走到死胡同。
**只看结果与体验，不看实现。**

## 前置
- 栈已起（`docker compose up -d`，账号密码见 `docs/demo-accounts.md`，统一 `admin123`）。
- Playwright MCP 已按 [B0 spike 笔记] 配好（`docs/superpowers/notes/2026-06-26-playwright-mcp-spike.md`）。

## v1 人物集（每个只给"我是谁+目标+账号+URL"，绝不给步骤）
| 人物 | 账号 | 目标 |
|------|------|------|
| 家长 | guardian-kg1 / admin123 | 登录后想知道孩子今天在园里有没有异常/告警，并查看相关通知 |
| 教师 | teacher-kg1 / admin123 | 想查看本班的检测事件，并对其中一个做复核 |
| 园长 | director-kg1 / admin123 | 想了解本园概况、看待审批/管理项 |
| 超管 | admin / admin123 | 想跨园查看平台层面的概况 |

> 人物基于 seed 既有数据探索；真实用户不会自己注入事件（注入的因果验证归 Tier-2）。

## 走查手法
1. 用 MCP 打开 URL → 取页面快照+截图 → **看到什么才动什么** → 每步截图落 `_workspace/visual-acceptance/<run>/NN-*.png`。
2. 全程以人物口吻记录：我想干啥 / 我看到啥 / 我做了啥 / 结果 / 我的感受。
3. 达成目标 = 该人物 happy path 通；中途卡住/绕远/困惑 = 记 finding。

## 判级与路由
- **恶性缺陷**（挡住核心任务 / 不可用）→ 人物 NO-GO → **挡发版**（写复现+证据截图）。
- **体验小建议**（papercut）→ 列入"给 Main Session 的非阻断建议"。
- finding 写法：「人物想做 X → 实际 Y → 期望 Z」+ 截图引用 + severity（按对人物任务的阻断度）+ `UX-` 前缀。

## 产出
`_workspace/visual-acceptance/<run>/report.md`：逐人物叙事 + 截图 + `UX-` findings +
**整体 GO/NO-GO**（任一人物恶性缺陷即 NO-GO）+ 非阻断建议清单。

## 覆盖与局限
- 人物主观判断、非确定性；价值在抓反人类设计，不替代 Tier-2 功能正确性。
- 外部 PUSH/SMS 不在范围（无真实投递凭据），闭环终点取站内通知。
- v1 仅 4 人物；其余角色任务后续扩。
