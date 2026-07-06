---
globs: frontend/**
disclosure: path-scoped
---

# Frontend 实现约定（`frontend/`）

App Router（`src/app/`），API 调用层全在 `src/services/apis/`，**双 HTTP 客户端并存**——RTK Query(`baseApi`) 与 Axios(`apiClient`)，两者均在拦截器注入 CSRF 头。Redux store 两个 reducer：`api`(RTK Query 缓存) + `user`(认证态)。会话恢复靠 `SessionBootstrap` 调 `GET /api/v1/auth/session`。

Next.js `output: 'export'` 纯静态导出，无 SSR。**前端绝不传 kindergartenId**（租户隔离靠后端 ThreadLocal 链，见 `security.md`）。

## 命令

```bash
# Frontend：本机有 node
cd frontend && npm run lint && npm run build    # next build → /out（静态导出）
```

- **本地 pre-push lint**：`git config core.hooksPath .githooks`（每次新克隆执行一次）；hook 仅在 push 含 `frontend/` 改动时跑 ESLint（本地 node 优先，回退 `node:20` 容器）。

## 已知陷阱

- **前端代码已被 git 追踪**（根 `.gitignore` 只忽略 `node_modules`/`.next`/`out`/env 变体，**非整树**）→ `Grep`/`Glob` 对 `frontend/` 正常可见，无需 `rg --no-ignore`。
