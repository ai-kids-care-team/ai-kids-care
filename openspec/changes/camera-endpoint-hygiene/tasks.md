# Tasks — camera-endpoint-hygiene (C6-gap a+b)

> 非破坏性。develop 直接提交。两 lane：backend + frontend。行为不变，只清契约债。

## Lane A — backend
- [x] A1 `CameraStreamController` GET list：`kindergartenId` 改 `@RequestParam(required=false)`（service 仍以 ThreadLocal `activeKindergartenId` 为准）
- [x] A2 `CctvCameraController` GET list：同上
- [x] A3 `EnumMetadataService.REGISTRY` 增 `"camera_stream_type" → CameraStreamTypeEnum.class`、`"protocol" → ProtocolEnum.class`
- [x] A4 测试：`GET /api/v1/enums/camera_stream_type`、`/protocol` 返回正确码序（仿现有 enums 端点测试）；两 GET 端点不传 kindergartenId 仍返回本租户数据
- [x] A5 `gradlew test` 全绿

## Lane B — frontend
- [ ] B1 `useCameraStreamsManagement`（及相关 GET 调用）去掉 `resolveViewerSessionKindergartenId` 传 kindergartenId 的 workaround（这两个 camera GET 不再发 kindergartenId）
- [ ] B2 `CameraStreamsSection` 改走 `/api/v1/enums/camera_stream_type`、`/protocol`（复用 `useStatusOptions` fetch+FALLBACK 模式），移除硬编码为纯 fallback
- [ ] B3 `npm run lint && npm run build` + `npm run test:run` 全绿；还原 next-env.d.ts

## 门禁
- [ ] G1 backend gradlew + frontend test/lint/build 全绿
- [ ] G2 integration 定向复核：两 GET 不传 kindergartenId 行为不变（本租户）；两 camera enum 三侧一致、前端确从 /enums 取
