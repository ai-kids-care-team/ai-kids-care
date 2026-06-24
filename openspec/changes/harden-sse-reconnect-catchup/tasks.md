## 1. 查询层

- [ ] 1.1 `DetectionEventRepository` 增 `findByKindergarten_IdAndIdGreaterThanOrderByIdAsc(Long kindergartenId, Long lastEventId, Limit limit)`(Spring Data `Limit`)。
- [ ] 1.2 `DetectionEventService` 增 `replaySince(Long kindergartenId, Long lastEventId, int max)` → 返回升序 `List<DetectionEventVO>`(若想要「超限取最近 max」,按 `event_id` desc 限 max 再反转,或先 count 决定下界——实现时择一并在测试固定语义)。

## 2. SSE 服务与端点

- [ ] 2.1 `DetectionEventSseService`:抽出私有 `sendEvent(SseEmitter emitter, DetectionEventVO vo)`(`.id(eventId).name("detection-event").data(vo)` + 失败 evict),供实时 `onIngested` 与补发共用(重构,保持现有测试绿)。
- [ ] 2.2 `DetectionEventSseService` 增 `replaySince(Long kgId, Long lastEventId, SseEmitter emitter)`:取 `service.replaySince(...)` 升序逐条 `sendEvent`;`try/catch` 失败即 `remove`。
- [ ] 2.3 `DetectionEventController` `/stream` 增 `@RequestHeader(value = "Last-Event-ID", required = false) String lastEventId`;解析为 `Long`(非数字/空→跳过补发);register 拿到 emitter 后,若有有效 `lastId` 调 `sseService.replaySince(kgId, lastId, emitter)`,再发 `connected`/接实时。

## 3. 配置

- [ ] 3.1 `application.yml` 增 `detection.sse.replay-max: 200`;`application-test.yml` 设一个便于测试的值(如 5,方便测上限)。

## 4. TDD

- [ ] 4.1 `DetectionEventSseServiceTest`(纯 Mockito,复用 `registerEmitter` seam):先写失败测试——`replaySince(kg, lastId, emitter)` 对 mock service 返回的事件逐条 `sendEvent`(升序、`id:`=eventId);看红→实现→绿。
- [ ] 4.2 补测试:补发某帧抛错 → 该 emitter 被 evict(`remove`),不影响其余。
- [ ] 4.3 集成/切片测试:`/stream` 带 `Last-Event-ID: n` → 仅补发本园 `event_id > n` 事件、升序、租户隔离(他园不补);无/非数字 header → 不补发、行为如旧;空窗超 `replay-max` → 只补最近 max。
- [ ] 4.4 确认现有 keepalive heartbeat 测试与实时推送测试仍绿(重构未回归)。

## 5. 验证

- [ ] 5.1 后端 DooD 全套件回归(`gradle:8.7-jdk21`,`cleanTest test`,host override/ryuk-disabled/挂 repo 根),0 fail。
- [ ] 5.2 自查:无 schema 迁移;无 `Last-Event-ID` 路径行为零变化(向后兼容)。
