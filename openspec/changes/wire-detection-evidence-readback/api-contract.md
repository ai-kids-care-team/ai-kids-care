# API 契约 — wire-detection-evidence-readback (D-STORE)

> **草案（propose 阶段，待维护者批准 MinIO/依赖后冻结）**。前后端唯一真源。
> 前端**绝不传 kindergartenId**（租户靠后端会话 ThreadLocal）。两个端点均为 **GET 读**（无 CSRF 负担）。
> 鉴权 = 会话 + staff 角色 + 租户 scoped + 事件归属；GUARDIAN 无权（隐藏 404）。

---

### list-event-evidence — 列某事件的证据元数据

- **路径**：`GET /api/v1/detection-events/{eventId}/evidence`
- **鉴权**：会话；镜像 detection-events 详情的 staff+租户策略。跨租户/不可见事件 → 404。
- **懒加载**：前端在事件卡展开/打开详情时才调（非列表页批量）。

#### 响应 `200`
- **VO 类名**：`EventEvidenceFileVO`（既有，**加 `contentPath` 字段**）；返回 `List<EventEvidenceFileVO>`。

| 字段 | 类型 | 可空 | 说明 |
|------|------|------|------|
| evidenceId | number | 否 | PK |
| eventId | number | 否 | 所属检测事件 |
| type | string(`IMAGE`\|`VIDEO`) | 否 | `evidence_file_type_enum` |
| mimeType | string | 否 | `image/jpeg`\|`image/png`\|`video/mp4` |
| createdAt | string(ISO-8601) | 否 | |
| hash | string | 否 | SHA-256（完整性/ETag） |
| contentPath | string | 是 | 后端内容端点相对路径 `/api/v1/event_evidence_files/{evidenceId}/content`；**旧 `file://` 行不可读时为 `null`** |
| available | boolean | 否 | 后端能否从对象存储取到（`file://` 旧行/缺对象 → `false`） |

- 事件无证据 → `200` + 空数组（非 404）。

#### 前端对齐点
- `eventEvidenceFiles.api.ts` → `getEventEvidence(eventId)` query。
- `DetectionEventsDashboard` 卡片证据区块：`available && type=IMAGE` → `<img src={contentPath}>`；
  `VIDEO` → `<video controls src={contentPath}>`；`!available` → "증거를 불러올 수 없습니다".

---

### get-evidence-content — 后端反代证据字节流

- **路径**：`GET /api/v1/event_evidence_files/{evidenceId}/content`
- **鉴权**：会话 + staff + 租户 + 归属（**每请求**重解析）；跨租户/GUARDIAN → 404。
- **行为**：后端按 `storage_uri`(对象 key) 从 MinIO `getObject` **流式回**（`StreamingResponseBody`，
  支持 `Range` 透传给 `<video>` seek）。MinIO 不暴露浏览器。

#### 响应
| 情况 | 状态码 | 说明 |
|---|---|---|
| 成功 | `200`（或 `206` Partial，带 Range 时） | `Content-Type = mime_type`；`ETag = hash`；body = 字节流 |
| 旧 `file://` 行 / 对象缺失 | `404` | body `{ "error": "..." }`；前端显"不可用" |
| 跨租户 / 非 staff / 事件不属本租户 | `404` | 隐藏存在性 |

#### 前端对齐点
- 直接作为 `<img>/<video>` 的 `src`（同源 cookie 鉴权，无需额外 header）。

---

## enum / 分页 / schema
- enum：`EvidenceFileTypeEnum`(IMAGE/VIDEO)、`MimeTypeEnum` 已存在，均后端内部，无三处同步。
- 无分页（单事件证据数少）。
- **无 schema 迁移**：复用既有 `event_evidence_files` 表。
