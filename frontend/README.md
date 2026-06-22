# AI Kids Care — Frontend

Next.js 16 静态导出（`output: 'export'`），由 **Nginx** 托管。所有数据请求在浏览器端通过 Axios 调用后端 REST API（`/api/v1`）完成；没有 Next.js 运行时服务器、SSR 或 Server Actions。

认证采用**服务端会话**（Spring Session + Redis + httpOnly cookie + CSRF，ADR-0016）；前端使用 `withCredentials: true` + `X-XSRF-TOKEN` header，无 Bearer token。

详细规格见 OpenSpec 能力规格（`openspec/specs/`，如 auth-authorization、appreciation-letters 等）。

## 本地开发命令

```bash
# 安装依赖（存在 peer dependency 冲突，需加 --legacy-peer-deps）
npm ci --legacy-peer-deps

# Lint 检查
npm run lint

# 静态构建（产物输出到 /out）
npm run build
```

开发时如需启动热重载服务器（仅本地调试，不等于生产形态）：

```bash
npm run dev
# 默认监听 http://localhost:3000
```

API 基础路径默认 `http://localhost:8080/api/v1`，可通过 `NEXT_PUBLIC_API_BASE_URL` 环境变量覆盖（参考 `.env.example`）。

## 生产部署

生产环境通过根目录 `docker-compose.yml` 启动：多阶段构建将 `npm run build` 产物（`/out`）拷入 `nginx:alpine` 镜像，由 Nginx 提供静态服务并反代 `/api/` 到后端。详见根目录 `README.md` 与 `frontend/Dockerfile`。
