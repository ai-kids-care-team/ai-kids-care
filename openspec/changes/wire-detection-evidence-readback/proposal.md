# wire-detection-evidence-readback (D-STORE / INT-02)

## Why

检测闭环的**写入侧已完整**:AI 在 `alarm_on` 时用 PyAV 产一段 `video/mp4` 证据 clip
（`ai/src/ai_app/utils/evidence_capture.py`）→ ingest payload 带 `evidence{uri,hash,type,mimeType}`
→ 后端 `DetectionIngestService` 落一行 `event_evidence_files`（表/实体/enum/复合 FK/索引全在 V1
baseline，`storage_uri` 列注释即写 "internal MinIO/NAS"）。**但读取侧全空**:
`EventEvidenceFileController` 是空类、`EventEvidenceFileService` 两个方法都 `@PreAuthorize("denyAll()")`、
`EventEvidenceFileVO` 不含任何可播放 URL、前端两套复核 UI（`DetectionEventsDashboard` inline 复核 /
`CctvAlertPanel`）都没有 `<video>`/`<img>` 证据位、全仓无任何对象存储组件。

结果:**教职员复核告警时看不到 AI 抓拍的画面**，只能看文字——这是核心价值链
（CCTV→AI→**教职员复核**→通知家长）复核环节上一个"数据已存、无人可读"的实打实缺口。本变更把
读取侧接通:证据落对象存储（MinIO）、后端按 eventId 鉴权签发可读内容、前端复核卡内嵌播放。

## What Changes

1. **对象存储（MinIO）入 compose**：新增 `minio` service + `minio_data` 卷 + bucket 初始化；backend
   加对象存储 SDK 依赖与 `${ENV}` 注入的 endpoint/credentials/bucket 配置（fail-fast，同既有密钥风格）。
   〔**部署面 + 依赖新增，须维护者批准**〕
2. **后端读取端点**（`EventEvidenceFileController`/`Service` 从 `denyAll()` 空壳做实）：
   - `GET /api/v1/detection-events/{eventId}/evidence` — 列该事件证据元数据（staff + 租户 scoped）。
   - `GET /api/v1/event_evidence_files/{evidenceId}/content` — **后端反代**从 MinIO 取字节流回（每请求
     鉴权，MinIO 不暴露给浏览器）。VO 加内容端点引用。
3. **前端复核卡内嵌证据**：`DetectionEventsDashboard` 事件卡展开时懒加载证据（IMAGE→`<img>` /
   VIDEO→`<video>`），指向后端 content 端点（cookie 鉴权）。
4. **demo 可跑**：seed 证据对象注入 MinIO（配 seed 的 `storage_uri`），让 demo 栈能真放出一段样例画面。

## Non-goals

- **不做 schema 迁移**：`event_evidence_files` 表/列/enum/FK/索引全就绪，`storage_uri` 是 varchar 直接容纳
  对象 key，**零 V2 Flyway**。
- **不做 AI 真写入 MinIO**：AI 侧当前把 clip 写**容器本地 `file://`**；改成上传对象存储是 evidence_capture
  的 `s3://` 演进，**属 AI 真写侧、随真流部署解锁**（[[ai-live-deploy-decisions]]）。本轮只做**可读侧** +
  用 seed 对象演示；旧 `file://` 行读不到时**降级为"证据不可用"**（不报错）。
- **不做** presigned 直连浏览器方案（见 design 岔口 A：为把 MinIO 留在内网、鉴权集中，选后端反代）。
- **不改** 写入链路（AI→ingest→落库）、通知/复核既有行为、去重键。
- **不做** 证据保留策略自动化（`retention_until`/`hold` 列保留但本轮不接生命周期清理）。

## 需维护者批准的点（破坏性/部署面）

- compose 加 MinIO service（部署面：base + prod/cd overlay 都要，watchtower 面）。
- backend 加对象存储 SDK 依赖。
- 生产 MinIO 的凭据/桶/网络（VPN 内网可达）留部署时定。
