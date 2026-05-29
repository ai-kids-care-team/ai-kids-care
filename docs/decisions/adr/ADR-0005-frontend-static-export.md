---
ADR: ADR-0005
title: "ADR-0005: 前端静态导出 + Nginx 托管"
status: Accepted (Retrospective)
date: 2026-05-29
deciders: 原始团队（逆向补记）
---

# ADR-0005: 前端静态导出 + Nginx 托管

> **回溯性 ADR**：描述代码现状，非新提案。

## 状态

Accepted (Retrospective)

## 背景

✅ 前端使用 Next.js 16（App Router）。`next.config.ts` 设置 `output: 'export'`，构建产物为纯静态文件（`/out`）。`frontend/Dockerfile` 用 `nginx:alpine` 托管，并在 `nginx.conf` 中将 `/api/` 反代到 `backend:8080`。

## 决策

前端以 **SSG 静态导出** 形式交付，由 **Nginx** 托管静态资源并作为后端 API 的反向代理；不部署 Next.js 运行时服务器。

## 后果

- **正面**：部署极简（静态文件 + 单 Nginx 容器），无 Node 运行时；前后端经同源 `/api/` 通信，规避部分 CORS 复杂度。
- **代价 / 约束**：
  - 放弃 SSR / Server Components / Route Handlers / 中间件等 Next 服务端能力——所有数据获取在浏览器端进行。
  - 🔶 React 19 与部分依赖存在 peer 冲突，构建用 `npm install --legacy-peer-deps` 且 `overrides three@0.183.2` 绕过。
- **影响范围**：`frontend/`。

## 考虑过的备选

- ❓ Next.js Node 服务端部署（SSR）——未采用；推断为部署简化优先。

## 关联

- [architecture/frontend-architecture.md](../../architecture/frontend-architecture.md)
