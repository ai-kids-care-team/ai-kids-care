# 产品概览（Product Overview）

## 它是什么

✅ **已确认**（根 `README.md`）：AI Kids Care 是面向**幼儿园（유치원）环境**的 AI 安全管理平台。它把以下事务统一在一个流程里管理：CCTV 摄像头、保护者（家长）、教职工、儿童、通知、公告、感谢信，以及**AI 异常行为检测事件**。

## 它解决的核心问题

🔶 **推断**（综合 schema + 功能模块）：幼儿园场景下，儿童安全事件（虐待、打斗、跌倒、走失等）难以靠人工 7×24 盯防。本系统的核心价值是：

1. **自动监测** — 用 AI 模型分析 CCTV 视频流，自动识别异常行为（见 [ai-architecture](../architecture/ai-architecture.md)）。
2. **事件闭环** — 检测事件可被值班人员复核（review）、流转状态（OPEN → IN_REVIEW → RESOLVED 等）、保留证据文件。
3. **及时通知** — 按规则把事件推送给相关保护者/教职工（Push / SMS / Email 渠道）。
4. **运营数字化** — 管理幼儿园、班级、教室、儿童、教师、保护者及其相互关系与排期。
5. **社区沟通** — 公告（announcement）与感谢信（appreciation letter）。

## 目标用户

✅ **已确认**（`user_role_enum`，`db/initdb/01_create_schema.sql:24`）系统定义五类角色：

- `GUARDIAN`（保护者/家长）
- `TEACHER`（教师）
- `KINDERGARTEN_ADMIN`（幼儿园管理员）
- `PLATFORM_IT_ADMIN`（平台 IT 管理员）
- `SUPERADMIN`（超级管理员，🔶 推断为行政监管方，见 `superadmins.department` 注释"행정청+부서 이름 e.g.서울시청 아동담당관" = 行政机关+部门，如"首尔市厅 儿童负责官"）

角色详情见 [personas-and-roles.md](personas-and-roles.md)。

## 端到端业务流程（运行时视角）

🔶 **推断**（综合数据模型与各组件职责；注意当前 AI 实时链路与后端持久化尚未打通，见下方"重要边界"）：

```text
[CCTV 摄像头] → 视频流(RTSP/FLV)
      │
      ▼
[AI 推理服务] VideoMAE 分类 + 持续性规则(persistence rule)判定告警
      │
      ├─(✅ 已实现) Pushover / SMS 实时告警
      │
      └─(❓ 待确认 未打通) 写入 detection_events / detection_sessions
                                   │
                                   ▼
                        [后端 API] 事件查询、复核(event_review)、证据(evidence)
                                   │
                                   ▼
                        [通知引擎] 按 notification_rules 生成 notifications
                                   │
                                   ▼
                        [前端] 监控大屏、事件列表、家长/教师查看与处理
```

## 重要边界与现状（必读）

> ❓ **待确认 / 重要现状**——这些会直接影响对"产品是否完整"的判断，详见 [open-questions](../modernization/open-questions.md)：

1. **AI 实时检测未写回数据库** ✅：`ai/` 目录中**没有任何**连接 PostgreSQL 或调用后端 API 的代码（仅有 Pushover/SMS 工具）。实时告警脚本 `stream_live_alert_service.py` 把告警发往 Pushover/SMS 并写本地 CSV，**不**落库到 `detection_events`。因此后端看到的检测事件目前来自**种子数据**（`db/initdb/42_detection_events_seed.sql`），而非线上 AI。根 README 也称实时链路为"실험"（实验）。
2. **后端鉴权当前处于关闭状态** ✅：`SecurityConfig.java` 中 JWT 过滤器被注释、`/api/v1/**` 全部 `permitAll()`。即任何人可无凭证访问所有 API（见 [security-architecture](../architecture/security-architecture.md)）。这更像 demo/开发态而非生产态。
3. **密码重置未实现** ✅：`AuthService.passwordResets()` 直接抛出 `"Not implemented"`。

## 项目阶段与协作背景

✅ **已确认**（根 `README.md`「향후 개발 방식 안내」「기여도와 역할」节）：

- 自 **2026-05-11** 起，后续开发积极采用 Vibe Coding 与 AI Agents。原因：团队从 3 人协作收缩为 1 人维护。
- 2026-05-11 之前的提交、功能与贡献，是既有贡献者以传统协作方式完成的成果。
- 贡献统计（截至 `2026-04-10` 前共 407 commits）：Zhang Junfan 장준범（项目负责人/架构师/主力，~79%）、korea4050-debug（后端+前端整合/DB/种子数据，~16%）、deokwoo-han（前端，CCTV 监控大屏/感谢信页面，~5%）。

> 此背景解释了为何后续文档与自动化（含本知识库）由 AI Agent 协助产出，也解释了部分"半成品/实验性"模块的存在。
