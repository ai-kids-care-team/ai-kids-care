# 提案 — refine-evidence-readback-robustness（证据可读健壮性收尾）

## Why

`wire-detection-evidence-readback`（D-STORE，已发版归档 `ba80bd7`）落地了 staff 证据反代读链路。四方门禁复核（code-review + security + integration）共识记录了 **4 个非阻塞观察项**——都不影响安全承重墙、也不改契约的成功路径，但会在**边缘情形**下让行为与 HTTP 语义/前端体验不够严谨。本 change 是一次**纯健壮性 polish**，把这 4 项一次收干净，不引入新能力、不碰部署面、零 schema 迁移。

四项（原样承接 D-STORE 收口报告的遗留 notes）：

1. **`available` 只验 URI scheme、不探对象存在** → list 可能标 `available=true`，但该对象其实已从 bucket 缺失，前端点开 content 才 404。语义应为「后端**当前真能取到**」。
2. **Range 不可满足时返 200 而非 416** → 请求 `Range: bytes=<超出文件末尾>` 时当前退化成回整体 200，违反 RFC 7233（应 `416 Range Not Satisfiable` + `Content-Range: bytes */<len>`）。
3. **ETag 未按 RFC 7232 加引号** → 当前 `ETag: <hash>` 是裸值，规范要求 ETag 是 quoted-string（`ETag: "<hash>"`），否则缓存中间件/条件请求可能不识别。
4. **前端 `<img>/<video>` 无 `onError` 兜底** → 若 content 端点意外 404/5xx（如对象刚被清理），卡片显示 broken image 而非降级文案，与 `!available` 的「증거를 불러올 수 없습니다」不一致。

## What Changes

- **后端 `available` 语义**：list 时对**非 `file://`** 的行做一次轻量 `statObject`（HEAD 级、不传字节）确认对象在 bucket 中存在 → 存在才 `available=true`。`file://` 旧行仍直接 `false`（不发网络请求）。
- **后端 content 端点 Range**：请求 Range 越界/不可满足 → `416` + `Content-Range: bytes */<len>`；合法 Range → 维持 `206`；无 Range → `200`。
- **后端 ETag**：`ETag` 值加双引号（quoted-string），content 与 list 保持一致。
- **前端**：证据 `<img>/<video>` 加 `onError` → 落到与 `!available` 相同的降级文案。

## Non-goals（明确不做）

- **不改鉴权/租户模型**：D-STORE 的 `DETECTION_EVENT_READ` + `kindergarten_id` join 谓词 + 隐藏 404 原样不动。
- **不引入证据保留/生命周期自动化**（`retention_until`/`hold` 接线仍是独立 follow-up）。
- **不碰部署面**：MinIO service/依赖/compose 三件套已就位，本 change 无 infra 改动、无 schema 迁移。
- **不做真证据帧写入**（`file://`→`s3://` 随真流解锁，仍是「暂不推真流」决策门下的挂起项）。
- **不做 CctvAlertPanel 卡片证据**（另列 follow-up）。
- **`statObject` 的 N 次探测代价**：证据数少（单事件几条）、list 懒加载（仅事件卡展开时），可接受；若未来量大再评估批量/缓存，本轮不预优化。
