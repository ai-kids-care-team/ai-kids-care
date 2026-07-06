# Tasks — cleanup-landmines-and-masking (C4)

> 非破坏性（删的均为零调用者死方法/死代码；无 schema/迁移/部署）。两 lane：backend + ai。TDD。

## Lane A — backend（Java）

### 1. SEC-09 删 landmine 死方法
- [ ] 1.1 删 `ChildrenService.listChildren(String,Pageable)` / `getChild(Long)`（保留已接线的 `listRelatedChildren`/`getRelatedChild`）
- [ ] 1.2 删 `NotificationService.listNotifications(String,Pageable)` / `getNotificationInternal(Long)` / `createNotification(NotificationCreateDTO)`（保留已接线的无参 `listNotifications()`/`getNotification()`）
- [ ] 1.3 删随之变孤儿的 DTO/import（若 `NotificationCreateDTO` 仅此处用则删；先 grep 确认）
- [ ] 1.4 `gradlew test` 确认无遗漏引用（编译失败=有活调用者，则回退保留并报告）

### 2. SEC-08b 删死方法
- [ ] 2.1 删 `EventReviewService.getLatestReview(Long)` + 误导注释（确认零调用者）。`getEventReview` load-then-filter **不动**

### 3. QLT-03 删死代码
- [ ] 3.1 删 `AuthService.passwordResets(AuthPasswordResetDTO)`
- [ ] 3.2 删 `AuthPasswordResetDTO`、`AuthPasswordResetsVO`（grep 确认仅死方法引用）

### 4. QLT-04b 补测试
- [ ] 4.1 新建 `AiModelServiceTest`（或 keyword 搜索测试），仿 `detection/DetectionEventKeywordSearchTest.java`（去租户维度）：keyword 大小写不敏感命中 / blank keyword=无过滤 / 分页透传
- [ ] 4.2 `gradlew test` 全绿

## Lane B — ai（Python）

### 5. SEC-10 修掩码正则
- [ ] 5.1 `ai/src/ai_app/live/alert_service.py::mask_url_credentials`：改为掩到 host 前**最后一个 `@`**（贪婪到最后一个 userinfo 分隔符），保留「正则、不 parse round-trip、不动 query/path」设计约束
- [ ] 5.2 扩展 `ai/tests/test_mask_url_credentials.py`：加 `rtsp://user:pa@ss@host/x` 等内嵌字面 `@` 对抗 case，断言无明文残留；既有 4 case 仍绿
- [ ] 5.3 `cd ai && PYTHONPATH=src python -m pytest tests/ -v` 全绿

## 门禁
- [ ] G1 backend `./gradlew test` 全绿；ai `pytest` 全绿
- [ ] G2 security 定向复核：确认删除项零调用者、无行为/契约变化、无隔离回归；SEC-10 掩码无残留
