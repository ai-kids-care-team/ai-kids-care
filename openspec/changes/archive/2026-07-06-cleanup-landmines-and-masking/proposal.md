## Why

2026-07-06 分析的一批**低风险、清晰界定**的零散债，经 C4 侦查逐项坐实：清理未接线的越权 landmine 死方法、删纯死代码、补一处零覆盖测试、修一处凭据掩码正则健壮性缺口。均为非破坏性代码清理/加固，无 schema/迁移/部署面。

- **SEC-09（landmine）**：`ChildrenService.listChildren/getChild`、`NotificationService.listNotifications(String,Pageable)/getNotificationInternal/createNotification` **无 `@PreAuthorize`、无租户谓词**（全表 `findAll`/`findByNameContains`）。侦查确认**controller 层零映射、全仓零调用者**，注释自称的用途（注册流程/契约测试）也已过期不实——是「未来有人 `@Autowired` 就引入越权洞」的 landmine。删除比给死代码续命更稳妥。
- **SEC-08b（死方法）**：`EventReviewService.getLatestReview` `@PreAuthorize("denyAll()")`、零调用者，注释声称的 guardian-notify 用途从未接线（`EventReviewedEvent` 未回调它）——误导性死方法。
- **QLT-03（死代码）**：`AuthService.passwordResets`（查库后无条件抛 `Not implemented`）+ 仅它引用的 `AuthPasswordResetDTO` + 从未被使用的 `AuthPasswordResetsVO`。与 UX-07（未来密码重置）是「处置互斥」非「复用冲突」——真实现时会整段重写，不复用此骨架。
- **QLT-04b（零测试）**：`AiModelService.listAiModels` keyword 搜索已实现却零测试覆盖（姊妹 `DetectionEventService` 有 220 行测试）。
- **SEC-10（掩码不健壮）**：`ai_app/live/alert_service.py:mask_url_credentials` 的 `re.sub(r"(://)[^@/]+@", ...)` 遇 userinfo 内含**未转义字面 `@`**（`rtsp://user:pa@ss@host`）只掩到第一个 `@`，剩余片段泄漏明文。

## What Changes

- **删 SEC-09 landmine**（backend）：删 `ChildrenService.listChildren/getChild`、`NotificationService.listNotifications(String,Pageable)/getNotificationInternal/createNotification`（确认零调用者后删；`gradlew test` 会即时暴露任何遗漏引用）。保留已接线的鉴权+租户版本重载。
- **删 SEC-08b 死方法**（backend）：删 `EventReviewService.getLatestReview` + 其误导注释。`getEventReview` 的 load-then-filter 属次要性能观察，**不动**。
- **删 QLT-03 死代码**（backend）：删 `passwordResets` 方法 + `AuthPasswordResetDTO` + `AuthPasswordResetsVO`。
- **补 QLT-04b 测试**（backend）：仿 `DetectionEventKeywordSearchTest`（去租户维度）给 `AiModelService.listAiModels` 补 keyword 命中/blank 无过滤/分页透传断言。
- **修 SEC-10 正则**（ai）：`mask_url_credentials` 改为掩到 host 前**最后一个 `@`**（保留「用正则不做 parse round-trip、不动 query/path」的既有设计约束）；扩展 `test_mask_url_credentials.py` 加内嵌字面 `@` 对抗 case。

## Non-goals（本轮明确移出，侦查判定不宜纳入）

- **QLT-06** 审批服务重复：域不同（园级 vs 平台级字段/表不同），合理重复，抽象风险>收益 → 不动。
- **QLT-07** loader 行为测试：需 testcontainers 双库、无先例无 CI 基建 → follow-up。
- **SEC-11** CORS 硬编码：dev-only、无生产环境、origins 是具体白名单非通配符 → 不动（待生产规划）。
- **INT-04** `NotificationRuleController`/`EventEvidenceFileController` 空骨架：是 D-STORE(INT-02)/UX-08 未来落点，删了要重建 → 留占位。`AiModelController` 已实现无问题，只是前端未消费 → 不动。
- **PRF-12** keyword 非 sargable：根治需 pg_trgm/GIN 索引=schema 变更（决策门 D-SCHEMA）→ follow-up。
- **双 CCTV 路由目录**：与 C5（实现中）时序重叠 → 降级 follow-up（宜用改 DB seed 的低风险方案，C5 归档后单独做）。
- 不碰 schema/迁移/部署；不改任何 wire 契约（删的都是零调用者方法）。
