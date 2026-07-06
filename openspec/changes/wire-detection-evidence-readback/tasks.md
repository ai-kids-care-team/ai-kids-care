# Tasks — wire-detection-evidence-readback (D-STORE)

> ⚠️ **须维护者批准后再实现**：compose 加 MinIO（部署面）+ backend 加对象存储 SDK 依赖。批准前止于 propose。

## 基础设施（须批准）
- [ ] `docker-compose.yml` 加 `minio` service（ports 9000/9001、卷 `minio_data`、root user/pwd env）+ backend `depends_on`。
- [ ] `.prod.yml` / `.cd.yml` 同步 MinIO（部署面、watchtower）。
- [ ] bucket 幂等初始化（bootstrap 或 init 容器）。
- [ ] `.env.example` 加 `MINIO_ENDPOINT/MINIO_ACCESS_KEY/MINIO_SECRET_KEY/MINIO_BUCKET` 占位。

## 后端
- [ ] `build.gradle` 加对象存储 SDK（`io.minio:minio` 或 AWS S3 v2）。
- [ ] MinIO 配置类 + `@NotBlank` fail-fast env 绑定（同 REDIS_PASSWORD 风格）。
- [ ] `EvidenceStoragePort` + `MinioEvidenceStorageAdapter`（`getObject` 流式，解析 `storage_uri`：s3://→key / 裸 key / file://→不可读）。
- [ ] `EventEvidenceFileService`：解 `denyAll()`，实现 `listByEvent(eventId)`（staff+租户+归属 scoped JPQL，带 kindergarten_id 谓词）+ content 取流；VO 加 `contentPath`/`available`。
- [ ] `EventEvidenceFileController` + `DetectionEventController`：加 `GET /detection-events/{eventId}/evidence` + `GET /event_evidence_files/{evidenceId}/content`（`StreamingResponseBody` + Range）。
- [ ] 测试：list 鉴权（staff 可读/GUARDIAN 404/跨租户 404）、content 反代（200 流、file:// 行 404、Range 206）、空证据事件返回空数组。

## 前端
- [ ] `services/apis/eventEvidenceFiles.api.ts`：`getEventEvidence(eventId)`（懒加载、绝不传 kindergartenId）。
- [ ] `DetectionEventsDashboard` 事件卡展开区加证据展示（img/video 指向 content 端点、`!available` 降级文案）。
- [ ] 类型对齐契约（EventEvidenceFileVO + contentPath/available）。

## demo
- [ ] seed 证据对象注入 MinIO（对齐 `44_event_evidence_files_seed.sql` 的 `storage_uri` key），demo 栈真放样例画面。

## 门禁
- [ ] 后端 `./gradlew test` + 前端 `npm run lint && npm run build` 绿。
- [ ] 安全复核（证据仅 staff+本租户+本事件、MinIO 内网、凭据不入日志、每请求重鉴权）+ 集成复核（契约双侧）。
- [ ] compose config 三向（demo/cd/prod）通过。
- [ ] archive。

## 明确不做（本轮 Non-goals）
- AI 真写入 MinIO（file://→s3:// 演进，随真流解锁）。
- presigned 直连方案。
- 证据保留/生命周期自动化（retention_until/hold 接线）。
- CctvAlertPanel 卡片证据（follow-up）。
