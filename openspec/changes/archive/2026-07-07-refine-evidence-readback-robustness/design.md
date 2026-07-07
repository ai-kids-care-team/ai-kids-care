# 设计 — refine-evidence-readback-robustness

承接 `wire-detection-evidence-readback` 的现状（后端反代读链路已在 `develop`）。本 change 只在既有落点上做健壮性收尾，无新组件、无新端点。

## 1. `available` 反映真实可取（statObject）

- 现状：`EventEvidenceFileMapper`/`EventEvidenceFileService` 组 VO 时按 `storage_uri` 的 scheme 定 `available`——`file://`→false，其余→true。不探对象是否真在 bucket。
- 改动：list 路径对**非 `file://`** 行调 `EvidenceStoragePort.exists(key)`（新增，底层 MinIO `statObject`，仅取元数据不下载）。
  - `statObject` 成功 → `available=true`、`contentPath` 照给。
  - 对象缺失（`ErrorResponseException` code `NoSuchKey`）→ `available=false`、`contentPath=null`。
  - `file://` 行 → 直接 `false`，**不发网络请求**（省一次 RTT）。
- 边界：`exists()` 只吞「对象不存在」类错误映射成 false；其它 IO/鉴权异常照抛（不静默成 false，避免把「MinIO 挂了」误报成「证据没了」）。content 端点自身的存在性判断维持不变（真取字节时缺失仍 404）。

## 2. Range 语义（416）

- 现状：content 端点解析 `Range`，合法给 206，其余走整体 200——**越界 Range 也回 200**。
- 改动：解析后判定 satisfiable：
  - `start >= length` 或非法区间 → `416` + `Content-Range: bytes */<length>`，空 body。
  - satisfiable → `206` + `Content-Range: bytes <start>-<end>/<length>`（不变）。
  - 无 `Range` 头 → `200` 全量（不变）。
- 复用既有 `StreamingResponseBody`；长度取自 `statObject` 的 size 或 DB 记录，避免为算长度全载入内存。

## 3. ETag quoted-string

- content 与 list 的 `ETag`/校验值统一为 `"<hash>"`（RFC 7232 §2.3 quoted-string）。纯 header 格式修正，值仍是已存的 SHA-256。

## 4. 前端 onError 兜底

- `DetectionEventsDashboard` 证据 `<img>/<video>` 加 `onError` handler → 切到与 `!available` 同一降级块（`증거를 불러올 수 없습니다`）。纯前端 UX，不改 api 层。

## 契约影响

- **无字段增删**：`EventEvidenceFileVO` 的 `available`/`contentPath` 字段不变，只是 `available` 的**取值判定更严**（true 现在意味「刚探到对象在」）。前端类型不变。
- **HTTP 语义细化**：新增 416 可能返回码 + ETag 引号，属既有 content 端点的响应细节，前端 `<img>/<video>` 无感（浏览器原生处理 Range/ETag）。
- api-contract.md 对应更新（available 语义 + 416 行）。

## 安全

- 不新增攻击面：`exists()` 走与 content 相同的 tenant-scoped 查询后才对已授权行做 statObject；不因探测泄漏跨租户存在性（跨租户行在 JPQL 阶段已隐藏 404，根本到不了 statObject）。MinIO 凭据/端点仍 `${ENV}` fail-fast、不入日志。
