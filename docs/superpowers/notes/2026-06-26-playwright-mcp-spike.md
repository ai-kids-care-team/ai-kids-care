# Spike: Playwright MCP + browser↔stack networking
**Date:** 2026-06-26  
**Branch:** task/visual-acceptance-impl  
**Purpose:** De-risk Tier-1 visual-acceptance mechanism before authoring the agent/skill (Task B1).

---

## 1. MCP image — verified

**Image:** `mcr.microsoft.com/playwright/mcp:latest`  
**Version:** 0.0.76 (pulled 2026-06-26)

Verified with:
```sh
MSYS_NO_PATHCONV=1 docker run --rm -i mcr.microsoft.com/playwright/mcp --help 2>&1 | head -30
MSYS_NO_PATHCONV=1 docker run --rm mcr.microsoft.com/playwright/mcp --version
# → "Version 0.0.76"
```

Image pulled successfully from `mcr.microsoft.com/playwright/mcp:latest`. **No Node on PATH on this machine — Docker launch is mandatory; `npx @playwright/mcp` is NOT available as a fallback here** (it would silently fail).

### Relevant flags

| Flag | Effect |
|------|--------|
| `--headless` | Run browser headless (required in Docker — no display) |
| `--browser <browser>` | Browser to use: `chrome`, `firefox`, `webkit`, `msedge`. Default is chromium-based. |
| `--isolated` | Keep browser profile in memory; do not persist to disk. Use for stateless spike runs. |
| `--no-sandbox` | Disable process sandbox (required inside Docker containers where the kernel namespace doesn't allow sandboxing) |
| `--host <host>` | Bind address for SSE transport (use `0.0.0.0` to expose outside container). Default: `localhost`. |
| `--port <port>` | Port for SSE transport. When omitted the server runs in **stdio** mode (default for Claude Code MCP). |
| `--caps <caps>` | Extra capabilities: `vision`, `pdf`, `devtools`. Use `vision` to enable screenshot/image responses. |
| `--image-responses <mode>` | `allow` (default) or `omit`. Must be `allow` to get screenshots back. |
| `--timeout-action <ms>` | Per-action timeout, default 5000 ms. |
| `--timeout-navigation <ms>` | Navigation timeout, default 60000 ms. |

---

## 2. Networking — proven via curl (already settled before this spike)

**Machine environment:** Windows 11, Docker Desktop (Linux containers). The compose stack (`frontend :80`, `backend :8080`) is running on the host.

**Chosen approach: `--network host`**

A `--network host` container reaches BOTH:
- `http://localhost:80` → frontend (200 OK, confirmed via curl)
- `http://localhost:8080` → backend (200 OK, confirmed via curl)

Because the static Next.js frontend's baked `NEXT_PUBLIC_API_BASE_URL` is `http://localhost:8080/api/v1`, a browser running inside a `--network host` container will resolve `localhost:8080` to the host backend directly. **No frontend rebuild is required.**

**Why this works on Docker Desktop / WSL2:** `--network host` lets the container share the Docker Linux VM's network namespace. WSL2 forwards `localhost` to the Windows host by default, so the host's `:80` and `:8080` are reachable as `localhost` from inside the namespace-sharing container. (Conclusion proven by curl returning 200 on both ports.)

**Alternatives NOT taken:**
- `host.docker.internal` + default bridge: Would require a frontend rebuild with `NEXT_PUBLIC_API_BASE_URL=http://host.docker.internal:8080/api/v1` — deferred as unnecessary.
- Port-mapped container (`-p 3000:3000`): Cannot reach host's `localhost:8080` without extra routing — not used.

---

## 3. Chosen MCP launch command

For use as a Claude Code stdio MCP server:

```sh
docker run --rm -i \
  --network host \
  mcr.microsoft.com/playwright/mcp:latest \
  --headless \
  --browser chromium \
  --no-sandbox \
  --isolated \
  --caps vision \
  --image-responses allow
```

> **Note on `--browser chromium`:** The help lists `chrome`, `firefox`, `webkit`, `msedge`. The image ships Chromium as its bundled browser; passing `chromium` may work (it is accepted by Playwright internally) but `chrome` is the official listed value. Task B1 should test both; fall back to omitting `--browser` to use the image default if neither produces a launch.

---

## 4. `.mcp.json` server entry (for Task B1)

Add to `.mcp.json` at project root (or `.claude/mcp.json`):

```json
{
  "mcpServers": {
    "playwright": {
      "type": "stdio",
      "command": "docker",
      "args": [
        "run", "--rm", "-i",
        "--network", "host",
        "mcr.microsoft.com/playwright/mcp:latest",
        "--headless",
        "--browser", "chromium",
        "--no-sandbox",
        "--isolated",
        "--caps", "vision",
        "--image-responses", "allow"
      ]
    }
  }
}
```

After adding this entry, reload the Claude Code session (`/mcp` → restart or re-open). The `playwright` MCP server tools will then be available in the session.

---

## 5. Live MCP validation — DEFERRED

**Why deferred:** This task runs as a subagent inside an already-active Claude Code session. Adding a new MCP server to `.mcp.json` requires a session reload to take effect. A subagent cannot connect a new MCP server to the running session, and attempting to fake the validation would be misleading.

**What Task B1 must do (manual check after session reload):**

1. Add the `.mcp.json` entry above to the project.
2. Restart / reload the Claude Code session (run `/mcp` to confirm `playwright` appears).
3. In the new session, ask Claude to:
   - Navigate to `http://localhost` (frontend)
   - Click the login button (`로그인`)
   - Fill in credentials: username `guardian-kg1`, password `admin123`
   - Submit and take a screenshot
   - Confirm the screenshot shows the logged-in guardian dashboard

This validates: (1) Claude can get page snapshots, (2) Claude can click/fill/submit via MCP tools, (3) screenshots are returned and visible, (4) the `--network host` networking lets the in-browser XHR to `:8080/api/v1` succeed.

---

## 6. Known limitations

| Limitation | Impact | Mitigation |
|-----------|--------|------------|
| `--network host` is Linux-only in native Docker | On Docker Desktop (Windows), the Linux VM has port-forwarding from Windows host; confirmed working. On WSL2 back-end the same applies. No issue on this machine. | If ever run on macOS: `--network host` doesn't work; use `host-gateway` extra-host instead. |
| `--no-sandbox` reduces isolation | Acceptable for local dev/CI acceptance. Never use in production serving untrusted URLs. | Scope: acceptance tests only, stack runs trusted local content. |
| Image has no `chromium` keyword in documented browser list | Could fail at launch if Playwright MCP rejects `chromium` as a browser name. | Fall back to omitting `--browser` (uses image default) or try `chrome`. |
| Headless may render differently from headed | Headless can differ from headed rendering (fonts / GPU-accelerated CSS); some visual glitches may be missed. | Accept DOM-level acceptance, or for pixel-accuracy run headed in an environment with a display. |
| Session reload required after `.mcp.json` change | One-time cost per new machine setup. | Document in Task B1 onboarding notes. |
| Stateless `--isolated` loses cookies between reconnects | Each Claude Code session starts fresh login state. | Pre-login step is included in the acceptance script. No persistent auth needed. |

---

## 7. Summary

| Item | Result |
|------|--------|
| Playwright MCP image | `mcr.microsoft.com/playwright/mcp:latest` (v0.0.76) — EXISTS and runs |
| Networking solution | `--network host` — PROVEN (curl 200 on :80 and :8080) |
| Frontend rebuild needed? | No |
| Live MCP smoke test | DEFERRED to Task B1 (session reload required) |
| `.mcp.json` entry | Ready above — copy/paste into Task B1 |
