# 设计 — wire-detection-evidence-readback (D-STORE)

## 现状（Explore 摸底，写入侧已全通）

- AI 产 clip：`ai/src/ai_app/utils/evidence_capture.py::save_and_hash` → `("file:///abs", sha256)`；
  `live/alert_service.py:437` 组 `evidence{uri,hash,type:VIDEO,mimeType:video/mp4}` 随 ingest 提交。
- 后端落库：`DetectionIngestService:84` `INSERT INTO event_evidence_files(...storage_uri, hash, hold=false...)`。
- 表：`event_evidence_files`（`evidence_id/event_id/kindergarten_id/type(IMAGE|VIDEO)/storage_uri/mime_type/created_at/retention_until/hold/hash`，复合 FK `(kindergarten_id,event_id)`）——**齐备，零迁移**。
- 读取侧：controller 空类、service `denyAll()`、VO 无 URL、前端无展示位、全仓无对象存储。

## 岔口决策（本 propose 的推荐选择）

### A. URL 签发策略 → **后端反代（recommended）**，非 presigned 直连
- **选后端反代**：`GET /event_evidence_files/{id}/content` 由后端从 MinIO 取字节流回。
  - 理由：MinIO **留在内网**不暴露浏览器（同"摄像头凭据只经后端"的安全姿态）；**每请求**做 staff 角色 +
    租户 + 事件归属鉴权（presigned 一旦签发，TTL 内绕过后端授权）；租户隔离在每字节请求处强制。
  - 代价：证据字节过后端连接。但证据是**偶发、小体积**（staff 复核时才看一段短 mp4），吞吐可接受；
    用 `StreamingResponseBody` + Range 透传避免占用大内存/长事务。
- **备选（不选）**：MinIO presigned 短 TTL 直连——更省后端带宽，但需把 MinIO ingress 暴露给浏览器 +
  签发后 TTL 内无法撤销。若日后证据体量/并发变大再重估。

### B. `storage_uri` 约定 + 旧行降级 → **存对象 key，file:// 降级不可用**
- 新写入约定：`storage_uri` 存**对象 key**（`evidence/{kindergartenId}/{eventId}/{evidenceId}.mp4` 量级），
  bucket 由 `${MINIO_BUCKET}` 配置定。后端反代 `getObject(bucket, key)`。
- 兼容：解析 `storage_uri`——`s3://bucket/key` 取 key、裸 key 直用、**`file://` → 视为"证据不可用"**
  返回 404 + 前端显"证据暂不可用"（AI 本地路径后端本就读不到，非错误）。seed 用 `s3://` 形式，注入 MinIO
  时按 key 放对象即可对齐。

### C. 前端嵌入位 → **inline 织进 DetectionEventsDashboard 事件卡（recommended）**
- 复核流最短（"看板内复核"），改动集中。事件卡**展开时懒加载**证据元数据（避免列表页批量请求）；
  IMAGE→`<img>`、VIDEO→`<video controls>` 指向后端 content 端点（同源 cookie 鉴权，无需额外 token）。
- `CctvAlertPanel` 卡片当前不可点开，本轮不动（可作 follow-up）。

## 鉴权（承重）

- 读端点鉴权 = **staff 角色 + 租户 scoped + 事件归属**：镜像 detection-events 列表/详情既有策略
  （`@PreAuthorize(@authorizationPolicy...)`）。证据查询 JPQL **必带 `kindergarten_id` 谓词**（经 join
  `event_evidence_files → detection_events`），跨租户/不可见 → 隐藏 404。家长（GUARDIAN）**无**证据读取权
  （证据是内部复核物料，非家长渠道）——沿用 detection-events 的角色边界。
- content 端点同样每请求重解析授权（会话式，撤销下一请求即生效）。

## 组件落点

- **compose**：`minio`（image `minio/minio`，`server /data --console-address :9001`，ports 9000/9001，
  卷 `minio_data`，root user/pwd env）；backend `depends_on: minio` + env
  `MINIO_ENDPOINT/MINIO_ACCESS_KEY/MINIO_SECRET_KEY/MINIO_BUCKET`（`@NotBlank` fail-fast）。bucket 由
  启动 bootstrap 幂等建（或一次性 init 容器）。prod/cd overlay 同步（部署面，待批）。
- **backend**：`build.gradle` 加 `io.minio:minio`（或 AWS S3 SDK v2）；新 `EvidenceStoragePort` +
  `MinioEvidenceStorageAdapter`（getObject 流式）；`EventEvidenceFileService` 解 denyAll、实现 list +
  content；VO 加 `contentPath`（相对后端端点路径，前端拼）。
- **前端**：`services/apis/eventEvidenceFiles.api.ts`（按 eventId 取证据列表，懒加载）；
  `DetectionEventsDashboard` 卡片加证据区块；类型加 evidence。绝不传 kindergartenId。

## 边界与安全红线

- 证据字节流经后端**不进事务**、设超时；`StreamingResponseBody` 避免全载入内存。
- `hash` 已存（SHA-256）：content 响应可选带 `ETag=hash` 便于缓存/完整性；本轮不强制校验（信任边界同
  ADR-0015：ingest 不校验 AI 文件）。
- MinIO 凭据走 `${ENV}` fail-fast，绝不入日志；`storage_uri`/key 不含 PII。
- 证据是**内部复核物料**：仅 staff、仅本租户、仅该事件——default-deny 兜底。
