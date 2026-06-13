# 前端开发指南（Frontend Guide）

✅ 来源：`frontend/`。架构总览见 [architecture/frontend-architecture.md](../architecture/frontend-architecture.md)。

## 目录约定

```text
src/
├── app/            # 路由（App Router）；(auth) 为路由组
├── components/     # 业务组件按域分；shared/ui 为通用原子组件
├── services/apis/  # 每域一个 *.api.ts；统一基于 apiClient.ts
├── store/          # Redux Toolkit：index.ts(store)/hook.ts/slices
├── config/api.ts   # API base URL 解析
├── types/          # 领域 TS 类型
└── layout/ utils/  # 布局与工具
```

## 调用后端 API

✅ 统一通过 `services/apis/apiClient.ts`（Axios 实例）：

- 已内置请求拦截（注入 `Bearer` token）与响应拦截（401→refresh→重放→失败则弹登录框）。
- 新增端点：在对应域的 `xxx.api.ts` 中基于 `apiClient` 封装函数，复用 `base.api.ts` 的约定。
- base URL 由 `config/api.ts` 决定（默认 `http://localhost:8080/api/v1`，可用 `NEXT_PUBLIC_API_BASE_URL` 覆盖）。

## 开发命令

```bash
npm install            # 冲突时：npm install --legacy-peer-deps
npm run dev            # http://localhost:3000
npm run lint
npm run build          # 产物为静态导出 /out
```

## 构建与部署注意

- ✅ `output: 'export'`（静态导出）——**无 SSR/Server 能力**，数据获取一律在浏览器端。
- ✅ Docker 构建注入 `NEXT_PUBLIC_API_BASE_URL=/api/v1`（相对路径，经 Nginx 反代）。
- 🔶 React 19 peer 冲突：构建用 `--legacy-peer-deps`，`package.json` 有 `overrides three@0.183.2`。新增依赖注意兼容。

## 关系图可视化

`reagraph`（WebGL）依赖和展示代码仍保留，但公共 `/graph/children/{childId}` 已因 S1 数据边界关闭；当前 UI 只显示待权限实现提示，不再发起关系图请求。`next.config.ts` 仍对 `reagraph`/`@react-three/*` 配置 `transpilePackages`。

## 状态管理

✅ Redux Toolkit，目前仅 `userSlice`（用户/令牌/角色）。新增全局状态在 `store/slices/` 添加 slice 并在 `store/index.ts` 注册；组件用 `store/hook.ts` 的类型化 hooks。

## 注意事项

- ⚠️ 前端实现了完整 JWT 流程，但后端当前鉴权关闭——本地行为可能与"鉴权开启"时不同（见 [security-architecture](../architecture/security-architecture.md)）。
- ⚠️ 无前端测试基线。
