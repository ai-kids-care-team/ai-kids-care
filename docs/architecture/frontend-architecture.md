# 前端架构（Frontend Architecture）

✅ 主要来源：`frontend/package.json`、`frontend/next.config.ts`、`frontend/src/`、`frontend/Dockerfile`、`frontend/nginx.conf`。

## 1. 技术栈

- **Next.js 16.1.6**（App Router）+ **React 19.2** + **TypeScript 5**。
- **Tailwind CSS v4** + **Radix UI**（大量原子组件）+ shadcn 风格 `components/shared/ui`。
- **Redux Toolkit 2.x** + react-redux（全局状态）。
- **Axios**（HTTP 客户端）。
- **reagraph**（WebGL 关系图可视化依赖仍保留；公共 Graph API 当前关闭）、**recharts**（图表）。
- 表单 `react-hook-form`，通知 `sonner`，日期 `react-day-picker` 等。

## 2. 构建与部署形态（关键）

✅ `next.config.ts`：`output: 'export'` → **纯静态导出**（SSG），产物为静态 HTML/JS（`/out`）。

含义：

- **没有 Next.js 运行时服务器**，无 SSR/Server Actions/Route Handlers。所有数据获取在浏览器端通过 Axios 调后端完成。
- ✅ `frontend/Dockerfile`：多阶段构建 → `npm run build` 产出 `/out` → 拷入 **nginx:alpine** 托管。
- ✅ 构建时注入 `NEXT_PUBLIC_API_BASE_URL=/api/v1`（相对路径），由 Nginx 反代到后端。
- ✅ `nginx.conf`：`location /` 用 `try_files $uri $uri.html $uri/index.html /index.html`（适配静态导出的路由）；`location /api/` 反代 `http://backend:8080/api/`，并转发 WebSocket 升级头。

## 3. 目录结构

✅ `frontend/src/`：

```text
src/
├── app/                 # Next App Router 路由（页面）
│   ├── (auth)/          #   signup / forgot-password / reset-password（路由组）
│   ├── announcements/   #   公告 read/write/edit
│   ├── appreciationLetter/, letters/  # 感谢信路由保留；当前显示暂不可用
│   ├── cctvCamera/, cctvCameras/      # CCTV；仅园级角色加载 live stream
│   └── detectionEvents/ #   检测事件占位页（授权接口落地前不读取）
├── components/          # 业务组件（按域：announcements/auth/cctv/detectionEvents/home/letters）
│   └── shared/ui/       #   通用 UI 原子组件（Radix 封装）
├── services/apis/       # 每域一个 API 模块（见下）
├── store/               # Redux：index.ts(store) / hook.ts / slices/userSlice.ts
├── config/api.ts        # API base URL 解析与规范化
├── types/               # TS 类型（child/cctv/announcement/detectionEvents/user-role…）
├── layout/, utils/      # 布局与工具（含 auth-modal）
```

## 4. API 客户端层

✅ `services/apis/apiClient.ts` 是核心 Axios 实例，封装当前过渡期的 bearer 注入与 401 处理：

**请求拦截器**：只从 `Redux store.user.token` 读取当前页面生命周期内的令牌，注入 `Authorization: Bearer <token>`。浏览器不再把 user/access/refresh token 写入或恢复自 `localStorage`。

**响应拦截器**：
- 收到 `401` → 清空 Redux 会话并调用 `openLoginModal()`。
- 当前不在浏览器持久化 refresh token，也不自动刷新或重放请求；刷新页面后需重新登录。目标 server-side session 仍由 ADR-0016 后续实现。

✅ `config/api.ts`：默认 base URL `http://localhost:8080/api/v1`，可由 `NEXT_PUBLIC_API_BASE_URL` 覆盖；并对大小写做规范化（`/api/V1`→`/api/v1`），还导出 `LEGACY_API_BASE_URL`（去掉 `/v1`）以兼容遗留端点。

✅ 各域 API 模块基于 `apiClient`/`base.api.ts` 封装具体端点调用；Graph、User/Child/Guardian/Teacher profile 与 DetectionEvent 的 helper 当前不发起公共请求，相关页面显示待授权提示或返回不可用状态。

## 5. 状态管理

✅ Redux Toolkit，目前仅 `userSlice`（用户/令牌/角色）。`store/hook.ts` 提供类型化 hooks。前端权限/菜单 🔶 推断由登录返回的 `role` + 后端 `/menus` 驱动。

## 6. 前后端鉴权预期 vs 现状（必读）

> ❓ 前端实现了完整的 **JWT + 刷新 + 401 处理**，且代码注释证明前端开发者**遇到过 401**。但后端当前 `permitAll` 且过滤器停用（[security-architecture](security-architecture.md)）。两者存在张力：
> - 可能后端鉴权是**近期被临时关闭**的（前端逻辑写于鉴权开启时期）；
> - 也可能不同分支/部署的后端配置不同。
> 这是需要与团队确认的重点之一，见 [open-questions](../modernization/open-questions.md)。

## 7. 已知特征 / 缺口

- 🔶 `npm install --legacy-peer-deps`（Dockerfile）——存在 peer dependency 冲突（React 19 + 部分库），靠该标志绕过。`package.json` 还 `overrides three@0.183.2`。
- 🔶 前端页面覆盖 < 后端 API 全集（见 [features](../product/features.md)）。
- ✅ 无前端测试（无测试脚本/框架于 `package.json`）。
