# Tasks — refine-evidence-readback-robustness

> 纯健壮性 polish，无部署面/无 schema 迁移。develop 直接提交。

## 后端

- [ ] `EvidenceStoragePort` 加 `boolean exists(String objectKey)`；`MinioEvidenceStorageAdapter` 实现（`statObject`，仅捕获 `NoSuchKey` → false，其它异常上抛）。
- [ ] `EventEvidenceFileService.listByEvent`：非 `file://` 行调 `exists()` 定 `available`；`file://` 行不发网络请求直接 `available=false`/`contentPath=null`。
- [ ] content 端点 Range：越界/不可满足 → `416` + `Content-Range: bytes */<len>`（空 body）；satisfiable 维持 `206`；无 Range 维持 `200`。文件长度用 `statObject` size 或 DB 记录，勿全载入内存。
- [ ] `ETag` 统一 quoted-string（`"<hash>"`），content + list 一致。
- [ ] 测试：
  - list — 对象存在 `available=true`、对象缺失 `available=false`+`contentPath=null`、`file://` 行 `available=false` 且**不触发 statObject**（mock 验证零网络调用）、MinIO 故障（非 NoSuchKey）不被吞成 false。
  - content — 越界 Range → `416`+`Content-Range: bytes */<len>`；合法 Range → `206`；ETag 带引号。

## 前端

- [ ] `DetectionEventsDashboard` 证据 `<img>/<video>` 加 `onError` → 切到 `!available` 同款降级文案（`증거를 불러올 수 없습니다`）。
- [ ] 类型/api 层无改动确认（`available`/`contentPath` 字段不变）。

## 契约

- [ ] `api-contract.md`（复用 D-STORE 归档契约的语义）：`available` 判定改为「后端刚探到对象存在」；content 端点补 `416` 返回码行 + ETag 引号说明。

## 门禁

- [ ] 后端 `./gradlew test` + 前端 `npm run lint && npm run build` 绿。
- [ ] 安全复核（`exists()` 不泄漏跨租户存在性、凭据不入日志）+ 集成复核（VO 字段不变、HTTP 语义前端无感）。
- [ ] archive。

## 明确不做（Non-goals）

- 证据保留/生命周期（retention_until/hold）。
- AI 真写入 MinIO（file://→s3://，随真流解锁）。
- CctvAlertPanel 卡片证据。
- statObject 批量/缓存预优化（数据量小，量大再评估）。
