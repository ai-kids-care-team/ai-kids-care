## Why

实时看板的 SSE 客户端在断连/进程重启的空窗期里 ingest 的检测事件会**丢失**:服务端虽然已给每帧设了 `id:`=`event_id`(`DetectionEventSseService:71`),却**不读**浏览器重连时自动回传的 `Last-Event-ID`;现有唯一 catch-up 是前端连上后拉「最近 N 条」列表,空窗若超过 N 条或跨度较大就会漏。`event_id` 是单调 IDENTITY,且有 `(kindergarten_id, event_id)` 唯一索引——天然适合做「大于游标」的高效补扫。本 change 用 SSE 原生的 `Last-Event-ID` 在**重连时**精确补发空窗事件,**零 schema**。

## What Changes

- `/api/v1/detection-events/stream` 读取 `Last-Event-ID` 请求头(`@RequestHeader(required=false)`)。
- 连接携带有效 `Last-Event-ID` 时,服务端按该客户端**当前园**补发 `event_id > lastId` 的检测事件(升序、与实时帧同样的 `id:`/payload),再接实时推送。
- 补发**租户隔离**(只按连接者活动园查询;`Last-Event-ID` 仅是数值下界,无法越权扩园)、**有上限**(配置 `detection.sse.replay-max`,默认 200;空窗超限则补发最近上限条,更旧的由列表 API 兜底)。
- 缺失/非数字 `Last-Event-ID` → 不补发,连接行为与现状完全一致。
- 复用既有 `event_id` 作游标——**不引入** schema、不引入服务端持久游标表。

## Capabilities

### New Capabilities
<!-- 无新增 capability。 -->

### Modified Capabilities
- `ai-detection`: 修改「Backend pushes detection events to the frontend on ingest」——把原先「persistent delivered-cursor 补扫 out-of-scope」一段更新为「重连用 `Last-Event-ID` 精确补发(见新 requirement);服务端持久 per-subscriber 游标 + 跨实例 fanout 仍 out-of-scope」;新增 requirement「Detection SSE reconnect replay via Last-Event-ID」规定补发的语义、租户隔离、上限与降级。

## Impact

- `backend/.../controller/DetectionEventController.java`(`/stream` 读 `Last-Event-ID`)。
- `backend/.../service/DetectionEventSseService.java`(抽出帧构造 helper;新增按游标补发到指定 emitter)。
- `backend/.../service/DetectionEventService.java` + `DetectionEventRepository.java`(新增按 `(kindergarten_id, event_id > lastId)` 升序、限量查询)。
- `backend/.../resources/application.yml`(+ `application-test.yml`):`detection.sse.replay-max` 配置。
- 测试:`DetectionEventSseServiceTest`(纯 Mockito)+ 一个 stream 重连补发的集成/切片测试。
- 非破坏:无 schema、无 API 路径变化;无 `Last-Event-ID` 时行为零变化。多实例 live fanout(Redis pub/sub)仍是独立 follow-up。
