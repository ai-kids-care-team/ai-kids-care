## 1. 查询层

- [x] 1.1 `DetectionEventRepository` 增 `findByKindergarten_IdAndIdGreaterThanOrderByIdAsc(Long kindergartenId, Long lastEventId, Limit limit)`(Spring Data `Limit`)。
  - 偏差:实际实现为 `...OrderByIdDesc`(降序 + `Limit`),由 service 反转回升序——这是「超限取最近 max」唯一确定性做法(升序 + Limit 会取到最旧 max,违反 D2)。tasks 1.2 已明确许可 desc+reverse。
- [x] 1.2 `DetectionEventService` 增 `replaySince(Long kindergartenId, Long lastEventId, int max)` → 返回升序 `List<DetectionEventVO>`(降序限 max 查询后反转;`max<=0` 返回空)。

## 2. SSE 服务与端点

- [x] 2.1 `DetectionEventSseService`:抽出私有 `sendEvent(SseEmitter emitter, DetectionEventVO vo)`(`.id(eventId).name("detection-event").data(vo)`,throws IOException),实时 `onIngested` 与补发共用;eviction 仍由调用方负责(现有测试保持绿)。
- [x] 2.2 `DetectionEventSseService` 增 `replaySince(Long kgId, Long lastEventId, SseEmitter emitter)`:取 `service.replaySince(...)`(传入注入的 `detection.sse.replay-max`)升序逐条 `sendEvent`;`try/catch` 失败即 `remove` 并中止该 emitter 的补发。
- [x] 2.3 `DetectionEventController` `/stream` 增 `@RequestHeader(value = "Last-Event-ID", required = false) String lastEventId`;解析为 `Long`(空/非数字→跳过补发);register 拿到 emitter 后,若有有效 `lastId` 调 `sseService.replaySince(kgId, lastId, emitter)`,再发 `connected`/接实时。

## 3. 配置

- [x] 3.1 `application.yml` 增 `detection.sse.replay-max: 200`;`application-test.yml` 设 `replay-max: 5`(便于测上限)。

## 4. TDD

- [x] 4.1 `DetectionEventSseServiceTest`(纯 Mockito;显式 new service 传 replayMax=5):`replaySince(kg, lastId, emitter)` 对 mock service 返回的事件逐条 `sendEvent`(升序、`id:`=eventId),并断言传入的 max == 配置值。
- [x] 4.2 补测试:补发某帧抛错 → 该 emitter 被 `remove`(后续 live push 不再达;补发中止),不影响其余 emitter。
- [x] 4.3 集成测试 `DetectionEventStreamReplayTest`:经内部 ingest API 造真事件,`/stream` 带 `Last-Event-ID: n` → 仅补本园 `event_id > n`、升序;租户隔离(他园不补);无/非数字 header → 不补、行为如旧;空窗超 `replay-max`(=5) → 只补最近 5。
- [x] 4.4 现有 `DetectionEventSseServiceTest` 心跳/实时推送测试与 `DetectionEventStreamAuthTest` 保持(重构未改其语义)。

## 5. 验证

- [ ] 5.1 后端 DooD 全套件回归(`gradle:8.7-jdk21`,`cleanTest test`,host override/ryuk-disabled/挂 repo 根),0 fail。(Lead 在收口运行)
- [x] 5.2 自查:无 schema 迁移;无 `Last-Event-ID` 路径行为零变化(向后兼容)。
