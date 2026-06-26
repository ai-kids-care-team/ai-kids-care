# Release Visual Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a two-tier pre-release acceptance system — a deterministic Playwright CI gate (Tier-2, hard-blocks releases) and a Claude "experience officer" agent that drives the browser via Playwright MCP to judge real-user UX (Tier-1).

**Architecture:** Two fully decoupled tiers. Tier-2 lives in repo-root `e2e/` as a deterministic Playwright suite authored from code understanding, wired into `release.yml` as a hard gate (assertion failure → `:version` not pushed). Tier-1 is a new standalone agent (`release-visual-validator`) + skill (`release-visual-acceptance`) driving a browser via Playwright MCP with no UI pre-knowledge, routing malignant defects to a release block and minor suggestions to the Main Session. The existing `experience-analyst` (static-only) is unchanged.

**Tech Stack:** Playwright `@playwright/test` (TypeScript), GitHub Actions (`release.yml`), Playwright MCP server (via Docker, since the dev machine has no Node on PATH), Docker Compose stack (db/backend/frontend), Claude Code agents/skills (Markdown).

**Design doc:** `docs/superpowers/specs/2026-06-26-release-visual-acceptance-design.md`

## Global Constraints

- **Demo credentials:** all demo accounts use password `admin123` (post-fix `c126fe4`). Login IDs: `guardian-kg1`, `teacher-kg1`, `director-kg1`, `admin`.
- **Login is a MODAL**, not a route: from `/`, click the TopBar `로그인` button to open `LoginModal`; inputs are `input[name="loginId"]` (placeholder `아이디를 입력하세요`) and `input[name="password"]` (placeholder `비밀번호를 입력하세요`); submit is the modal's `로그인` button. Source: `frontend/src/components/home/LoginModal.tsx:117-177`, `frontend/src/layout/TopBar.tsx:81-87`.
- **All roles land on `/`**; role is distinguished by visible TopBar nav links (`frontend/src/config/menu.ts:38-45`): TEACHER/KINDERGARTEN_ADMIN see `대시보드` (→`/cctvCameras`); GUARDIAN/SUPERADMIN see `이상 탐지` (→`/detectionEvents`) but NOT `대시보드`.
- **Detection dashboard:** route `/detectionEvents`, heading `실시간 이상행동 감지`, events render as `ul > li`, review buttons `해결`/`에스컬레이션`/`기각` (TEACHER/ADMIN only). Source: `frontend/src/components/detectionEvents/DetectionEventsDashboard.tsx:168,184-242`.
- **Notification inbox:** route `/notifications`, heading `알림 수신함`, items show `title`/`body`/`createdAt`. Source: `frontend/src/components/notifications/NotificationsListForm.tsx:44,64-103`.
- **Frontend↔backend origin:** frontend is a static export (`output: 'export'`, `frontend/next.config.ts:4`) whose baked API base defaults to `http://localhost:8080/api/v1` (`frontend/src/config/api.ts:1-14`), `withCredentials: true`. The browser calls the backend cross-origin at `localhost:8080`. This means: a browser running on the same host as the published ports works (manual + CI-native); a browser inside a container does NOT (its `localhost` ≠ host) unless networking is solved — see Task B0.
- **Detection injection is via internal ingest only:** `POST /api/v1/internal/detection-sessions` then `POST /api/v1/internal/detection-events`, `Authorization: Bearer <AI_SERVICE_TOKEN>`, CSRF-exempt. Canonical sequence: `ai/scripts/inject_demo_event.py`. Never via a UI step.
- **Closed-loop notification endpoint = in-app inbox only.** External Pushover/SMS is out of scope for both tiers (no real delivery creds).
- **No Node on the dev machine:** all Node/Playwright execution is via Docker (`mcr.microsoft.com/playwright:v1.49.1-jammy`) or native on the CI runner. Never assume local `node`/`npx`.

---

## Part A — Tier-2: Deterministic Playwright CI Gate

### Task A1: Scaffold the `e2e/` project

**Files:**
- Create: `e2e/package.json`
- Create: `e2e/playwright.config.ts`
- Create: `e2e/.gitignore`
- Create: `e2e/README.md`
- Create: `e2e/tests/.gitkeep`

**Interfaces:**
- Produces: an `e2e/` Playwright project runnable via `npx playwright test`; config exposes `BASE_URL` (frontend, default `http://localhost`) and reads `AI_SERVICE_TOKEN` + `API_BASE_URL` (backend, default `http://localhost:8080`) from env for the injection helper.

- [ ] **Step 1: Create `e2e/package.json`**

```json
{
  "name": "ai-kids-care-e2e",
  "private": true,
  "version": "0.0.0",
  "description": "Release acceptance E2E (Tier-2 hard gate). Deterministic Playwright suite authored from code understanding.",
  "scripts": {
    "test": "playwright test",
    "report": "playwright show-report"
  },
  "devDependencies": {
    "@playwright/test": "1.49.1"
  }
}
```

- [ ] **Step 2: Create `e2e/playwright.config.ts`**

```ts
import { defineConfig, devices } from '@playwright/test';

// Frontend origin (static export). The browser must run where localhost:8080
// (the backend) is also reachable — the CI runner (native) or a host-networked
// container. See plan Task B0 / Global Constraints.
const BASE_URL = process.env.BASE_URL ?? 'http://localhost';

export default defineConfig({
  testDir: './tests',
  // A hung release gate is worse than a slow one; bound every test.
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // Pre-release gate: never run flaky-parallel; one retry absorbs transient
  // SSE/render races without masking a real regression.
  fullyParallel: false,
  workers: 1,
  retries: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: BASE_URL,
    screenshot: 'on',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
```

- [ ] **Step 3: Create `e2e/.gitignore`**

```gitignore
node_modules/
playwright-report/
test-results/
```

- [ ] **Step 4: Create `e2e/README.md`**

````markdown
# Release Acceptance E2E (Tier-2)

Deterministic Playwright gate. Authored from code understanding — NOT generated
by the Tier-1 visual agent. Hard-blocks releases in `.github/workflows/release.yml`.

## What it covers (v1)
- 4-role login + landing (`tests/login.spec.ts`)
- Flagship closed loop: inject detection event → teacher escalates → guardian
  in-app inbox shows the notification (`tests/closed-loop.spec.ts`)

External Pushover/SMS delivery is out of scope (no real creds).

## Running

The browser must run where both the frontend (`:80`) and backend (`:8080`) are
reachable as the SAME host the published compose ports live on.

**CI (authoritative):** runs natively on the GitHub runner — see `release.yml`.

**Locally on Linux/WSL (host networking):**
```sh
docker run --rm --network host -e BASE_URL=http://localhost \
  -e API_BASE_URL=http://localhost:8080 -e AI_SERVICE_TOKEN="$AI_SERVICE_TOKEN" \
  -v "$PWD/e2e:/e2e" -w /e2e mcr.microsoft.com/playwright:v1.49.1-jammy \
  sh -c "npm ci && npx playwright test"
```

**Validate without tagging:** trigger the `release.yml` `workflow_dispatch`.
````

- [ ] **Step 5: Create `e2e/tests/.gitkeep`** (empty file so the dir exists before specs land)

- [ ] **Step 6: Verify the project lists (no tests yet, but config parses)**

Run:
```sh
docker run --rm -v "$(cygpath -w "$PWD/e2e")":/e2e -w /e2e \
  mcr.microsoft.com/playwright:v1.49.1-jammy sh -c "npm ci && npx playwright test --list"
```
Expected: `npm ci` installs `@playwright/test`; `--list` prints `Total: 0 tests in 0 files` (or "no tests found") with exit 0 — config parsed OK.

- [ ] **Step 7: Commit**

```sh
git add e2e/package.json e2e/playwright.config.ts e2e/.gitignore e2e/README.md e2e/tests/.gitkeep
git commit -m "test(e2e): scaffold repo-root Playwright project for release acceptance (Tier-2)"
```

---

### Task A2: 4-role login + landing spec

**Files:**
- Create: `e2e/tests/login.spec.ts`
- Create: `e2e/helpers/login.ts`

**Interfaces:**
- Consumes: `e2e/playwright.config.ts` `baseURL`.
- Produces: `loginViaModal(page, loginId, password)` helper reused by `closed-loop.spec.ts`.

- [ ] **Step 1: Write the login helper `e2e/helpers/login.ts`**

```ts
import { Page, expect } from '@playwright/test';

/**
 * Logs in through the real UI: open the TopBar login modal, fill the form, submit.
 * Mirrors how a real user authenticates (the frontend handles CSRF itself).
 * Asserts the modal closed (= success). Selectors from LoginModal.tsx:117-177.
 */
export async function loginViaModal(page: Page, loginId: string, password: string): Promise<void> {
  await page.goto('/');
  // TopBar "로그인" opens the modal (TopBar.tsx:81-87).
  await page.getByRole('button', { name: '로그인' }).first().click();
  await page.locator('input[name="loginId"]').fill(loginId);
  await page.locator('input[name="password"]').fill(password);
  // The modal's submit button (scope to the open dialog to avoid the TopBar one).
  await page.getByRole('dialog').getByRole('button', { name: /^로그인/ }).click();
  // Success = modal closed: the loginId input is gone.
  await expect(page.locator('input[name="loginId"]')).toHaveCount(0, { timeout: 10_000 });
}
```

- [ ] **Step 2: Write the failing spec `e2e/tests/login.spec.ts`**

```ts
import { test, expect } from '@playwright/test';
import { loginViaModal } from '../helpers/login';

// Role → nav discriminator (menu.ts:38-45). All roles land on '/'.
const ROLES = [
  { loginId: 'teacher-kg1',  sees: '대시보드',  hidesDashboard: false },
  { loginId: 'director-kg1', sees: '대시보드',  hidesDashboard: false },
  { loginId: 'guardian-kg1', sees: '이상 탐지', hidesDashboard: true },
  { loginId: 'admin',        sees: '이상 탐지', hidesDashboard: true },
];

for (const role of ROLES) {
  test(`${role.loginId} can log in with admin123 and reach their home`, async ({ page }) => {
    await loginViaModal(page, role.loginId, 'admin123');
    await expect(page.getByRole('link', { name: role.sees })).toBeVisible();
    if (role.hidesDashboard) {
      await expect(page.getByRole('link', { name: '대시보드' })).toHaveCount(0);
    }
  });
}

test('wrong password is rejected (sanity: the gate can actually fail)', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: '로그인' }).first().click();
  await page.locator('input[name="loginId"]').fill('teacher-kg1');
  await page.locator('input[name="password"]').fill('definitely-wrong');
  await page.getByRole('dialog').getByRole('button', { name: /^로그인/ }).click();
  // Modal stays open (login failed): the input is still present.
  await expect(page.locator('input[name="loginId"]')).toBeVisible();
});
```

- [ ] **Step 3: Run the spec against the running local stack**

Ensure the stack is up (`docker compose ps` shows backend/frontend healthy). Then, on Linux/WSL:
```sh
docker run --rm --network host -e BASE_URL=http://localhost \
  -v "$(cygpath -w "$PWD/e2e")":/e2e -w /e2e \
  mcr.microsoft.com/playwright:v1.49.1-jammy sh -c "npm ci && npx playwright test login.spec.ts"
```
Expected: 5 passed (4 role logins + 1 sanity rejection). If a role locator fails, re-read `menu.ts:38-45` and adjust the `sees` text to the exact rendered label.

> If `--network host` does not expose `localhost:80` on this machine (Windows Docker Desktop), validate via the `release.yml` `workflow_dispatch` instead (Task A4 makes that runnable), or run under WSL. Do not weaken the assertions to make them pass.

- [ ] **Step 4: Commit**

```sh
git add e2e/helpers/login.ts e2e/tests/login.spec.ts
git commit -m "test(e2e): 4-role login + landing acceptance (Tier-2)"
```

---

### Task A3: Flagship closed-loop spec

**Files:**
- Create: `e2e/tests/closed-loop.spec.ts`
- Create: `e2e/helpers/inject.ts`

**Interfaces:**
- Consumes: `loginViaModal` (Task A2); `AI_SERVICE_TOKEN`, `API_BASE_URL` env.
- Produces: `injectDetectionEvent(request, opts)` returning `{ dedupKey, eventType }` used by the spec to find the event in the UI.

- [ ] **Step 1: Discover the exact ingest payload + the notification-triggering review outcome**

Read and record (do NOT guess):
1. `ai/scripts/inject_demo_event.py` — the exact JSON for `POST /api/v1/internal/detection-sessions` and `POST /api/v1/internal/detection-events` (field names, how `dedup_key` is generated, required `streamId`/`modelId`).
2. The internal ingest controller (search `frontend`-independent backend: `rg "internal/detection-events" backend/src/main`) to confirm field names/casing.
3. The event-review → guardian-dispatch wiring: `rg -n "dispatch|guardian|ESCALATED|RESOLVED" backend/src/main/java/com/ai_kids_care/v1/service` and the event-review service. Confirm WHICH `result_status` triggers a guardian notification. Per project memory, `ESCALATED` always notifies (penetrates quiet hours); `RESOLVED` is optional. Use the outcome that deterministically dispatches — expected: **escalate (`에스컬레이션`)**.

Write the confirmed payload + chosen outcome as a comment block at the top of `inject.ts`.

- [ ] **Step 2: Write the injection helper `e2e/helpers/inject.ts`**

Using the payload confirmed in Step 1 (example shape shown; replace field names/values with the verified ones from `inject_demo_event.py`):

```ts
import { APIRequestContext } from '@playwright/test';

const API = process.env.API_BASE_URL ?? 'http://localhost:8080';
const TOKEN = process.env.AI_SERVICE_TOKEN ?? '';

export interface InjectResult { dedupKey: string; eventType: string; }

/**
 * Replicates ai/scripts/inject_demo_event.py via the internal ingest endpoints
 * (Bearer AI_SERVICE_TOKEN, CSRF-exempt). Returns identifiers to locate the
 * event in the teacher dashboard. Field names verified in Task A3 Step 1.
 */
export async function injectDetectionEvent(
  request: APIRequestContext,
  opts: { streamId?: number; modelId?: number; eventType?: string; confidence?: number } = {},
): Promise<InjectResult> {
  const { streamId = 1, modelId = 1, eventType = 'ASSAULT', confidence = 0.94 } = opts;
  const headers = { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' };

  // 1) Open/resolve a detection session (payload per inject_demo_event.py).
  const sessionResp = await request.post(`${API}/api/v1/internal/detection-sessions`, {
    headers, data: { streamId, modelId },
  });
  if (!sessionResp.ok()) throw new Error(`session ingest failed: ${sessionResp.status()} ${await sessionResp.text()}`);
  const sessionId = (await sessionResp.json()).sessionId;

  // 2) Emit the detection event. dedupKey is unique per run so the UI assertion
  //    targets THIS event (index/time varies, dedupKey does not).
  const dedupKey = `e2e-${eventType}-${streamId}-${process.env.RUN_ID ?? 'local'}-${Date.now()}`;
  const eventResp = await request.post(`${API}/api/v1/internal/detection-events`, {
    headers,
    data: { sessionId, eventType, confidence, dedupKey },
  });
  if (!eventResp.ok()) throw new Error(`event ingest failed: ${eventResp.status()} ${await eventResp.text()}`);

  return { dedupKey, eventType };
}
```

> Adjust `sessionId`/`dedupKey`/`confidence` field names to whatever Step 1 confirmed. If the backend derives `dedupKey` server-side, instead capture the returned event id and locate by the event's visible time + type.

- [ ] **Step 3: Write the failing spec `e2e/tests/closed-loop.spec.ts`**

```ts
import { test, expect } from '@playwright/test';
import { loginViaModal } from '../helpers/login';
import { injectDetectionEvent } from '../helpers/inject';

test('detection → teacher escalation → guardian in-app notification', async ({ page, request }) => {
  // 1) A detection event exists (injected via the same internal path a real AI uses).
  const { eventType } = await injectDetectionEvent(request, { eventType: 'ASSAULT' });

  // 2) Teacher sees it on the live dashboard and escalates it.
  await loginViaModal(page, 'teacher-kg1', 'admin123');
  await page.goto('/detectionEvents');
  await expect(page.getByRole('heading', { name: '실시간 이상행동 감지' })).toBeVisible();
  const eventItem = page.locator('ul > li').filter({ hasText: eventType }).first();
  await expect(eventItem).toBeVisible({ timeout: 15_000 }); // allow SSE/poll to surface it
  await eventItem.getByRole('button', { name: '에스컬레이션' }).click();
  // No error toast/text on success.
  await expect(eventItem.getByText('검토 확정에 실패했습니다')).toHaveCount(0);

  // 3) Guardian sees an in-app notification for it.
  await page.goto('/'); // drop teacher session context before switching
  await loginViaModal(page, 'guardian-kg1', 'admin123');
  await page.goto('/notifications');
  await expect(page.getByRole('heading', { name: '알림 수신함' })).toBeVisible();
  await expect(page.getByText('알림을 불러오는 중입니다')).toHaveCount(0, { timeout: 10_000 });
  // At least one notification is present (empty-state text absent).
  await expect(page.getByText('받은 알림이 없습니다.')).toHaveCount(0);
});
```

> Note on session switching: if logging in as a second user requires an explicit logout, add a `logout(page)` helper (find the logout control in `TopBar.tsx`) and call it before the guardian login. Confirm during Step 1 discovery whether the modal login overrides an existing session or requires logout first.

- [ ] **Step 4: Run it against the live stack**

```sh
docker run --rm --network host -e BASE_URL=http://localhost \
  -e API_BASE_URL=http://localhost:8080 -e AI_SERVICE_TOKEN="$AI_SERVICE_TOKEN" \
  -v "$(cygpath -w "$PWD/e2e")":/e2e -w /e2e \
  mcr.microsoft.com/playwright:v1.49.1-jammy sh -c "npm ci && npx playwright test closed-loop.spec.ts"
```
Expected: 1 passed. If the guardian shows no notification, the review outcome chosen in Step 1 may not dispatch to guardians — re-check the dispatch wiring and switch to the outcome that does. Keep the assertion strict.

- [ ] **Step 5: Commit**

```sh
git add e2e/helpers/inject.ts e2e/tests/closed-loop.spec.ts
git commit -m "test(e2e): flagship closed-loop acceptance — inject → escalate → guardian inbox (Tier-2)"
```

---

### Task A4: Wire Tier-2 into `release.yml` as a hard gate

**Files:**
- Modify: `.github/workflows/release.yml` (the `build-smoke` job, after the existing frontend smoke step at `:155-169`, before the teardown at `:171-173`).

**Interfaces:**
- Consumes: the running compose stack (already up in `build-smoke`); the job-level env (`AI_SERVICE_TOKEN` etc., `release.yml:38-44`).
- Produces: a release-blocking step; screenshots uploaded as an artifact.

- [ ] **Step 1: Add the Playwright gate step (native on the runner — localhost reaches both `:80` and `:8080`)**

Insert after the `Smoke-test frontend HTTP response` step:

```yaml
      # ── 5b. Tier-2 release acceptance gate (deterministic Playwright).
      # Runs natively on the runner so the browser's localhost == the published
      # compose ports. Any assertion failure fails the job → :version is NOT pushed.
      - name: Set up Node for E2E
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Install E2E deps + browsers
        working-directory: ./e2e
        run: |
          npm ci
          npx playwright install --with-deps chromium

      - name: Run release acceptance E2E (hard gate)
        working-directory: ./e2e
        env:
          BASE_URL: http://localhost
          API_BASE_URL: http://localhost:8080
          AI_SERVICE_TOKEN: ${{ env.AI_SERVICE_TOKEN }}
          RUN_ID: ${{ github.run_id }}
        run: npx playwright test

      - name: Upload E2E screenshots + report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: release-acceptance-${{ github.ref_name }}
          path: |
            e2e/test-results/
            e2e/playwright-report/
          retention-days: 14
          if-no-files-found: warn
```

- [ ] **Step 2: Validate the workflow YAML parses**

Run:
```sh
docker run --rm -v "$(cygpath -w "$PWD")":/w -w /w mikefarah/yq \
  'true' .github/workflows/release.yml >/dev/null && echo "YAML OK"
```
Expected: `YAML OK` (yq parsed the file).

- [ ] **Step 3: Commit**

```sh
git add .github/workflows/release.yml
git commit -m "ci(release): add Tier-2 Playwright acceptance as a hard pre-publish gate"
```

- [ ] **Step 4: End-to-end validate via `workflow_dispatch` (no tag)**

After this branch is merged to `develop` (or pushed), trigger the Release workflow manually:
```sh
gh workflow run "Release – Build, Smoke, Push to GHCR" --ref develop
gh run watch
```
Expected: the `Run release acceptance E2E (hard gate)` step passes; artifact `release-acceptance-develop` is uploaded. (The push-`:version` step is tag-gated, so dispatch validates the gate without publishing.)

---

## Part B — Tier-1: Visual Experience Officer (local)

### Task B0: Spike — Playwright MCP + browser↔stack networking on the no-Node machine

**Files:**
- Create: `docs/superpowers/notes/2026-06-26-playwright-mcp-spike.md` (findings)

**Why first:** the dev machine has no Node on PATH, and the static frontend's browser calls `localhost:8080`. A browser inside a container can't reach the host's `:8080` as `localhost`. This task de-risks Tier-1 before authoring the agent/skill. Do not proceed to B1 until this produces a working configuration.

- [ ] **Step 1: Confirm a Docker-launchable Playwright MCP server**

Verify the image runs and exposes the MCP stdio interface:
```sh
docker run --rm -i mcr.microsoft.com/playwright/mcp --help 2>&1 | head -20
```
Expected: Playwright MCP usage/help prints. Record the exact image tag and any flags (`--headless`, `--browser chromium`, `--isolated`) in the notes file. If that image name differs, find the current official Playwright MCP image and record it.

- [ ] **Step 2: Solve the origin problem — pick ONE and prove it**

Try in order; record which works:

(a) **Host networking + host-resolved API.** Run the MCP browser container with `--network host` (WSL/Linux) and confirm the browser can load `http://localhost:80` AND that page's XHR to `http://localhost:8080/api/v1` succeeds (log in as `guardian-kg1`/`admin123` and see the app react).

(b) **Rebuild frontend with a host-reachable API base.** Bring up a local-only frontend whose `NEXT_PUBLIC_API_BASE_URL=http://host.docker.internal:8080/api/v1`, and run the MCP container able to resolve `host.docker.internal`. Confirm login works from inside the container browser.

Prove success by a manual MCP-driven login (see Step 3). Record the winning configuration verbatim (compose/run commands, env) in the notes file — B1/B3 depend on it.

- [ ] **Step 3: Smoke the MCP from Claude**

With the MCP server configured (temporarily, in `.mcp.json` per the winning config), have Claude: open `http://localhost`, click `로그인`, fill `guardian-kg1`/`admin123`, submit, and take a screenshot showing the logged-in state. Confirm Claude can (1) get page snapshots, (2) act on them, (3) retrieve screenshots.

- [ ] **Step 4: Write the spike notes + commit**

Record: chosen MCP launch command, chosen networking approach, any frontend rebuild needed, and known limitations.
```sh
git add docs/superpowers/notes/2026-06-26-playwright-mcp-spike.md
git commit -m "docs(spike): validated Playwright MCP + browser↔stack networking for Tier-1"
```

---

### Task B1: Add the Playwright MCP server to `.mcp.json`

**Files:**
- Create or Modify: `.mcp.json` (repo root)

**Interfaces:**
- Consumes: the winning launch command from Task B0.
- Produces: an MCP server named `playwright` available to the `release-visual-validator` agent.

- [ ] **Step 1: Add the server entry (using B0's verified command; example for the Docker host-networking variant)**

```json
{
  "mcpServers": {
    "playwright": {
      "command": "docker",
      "args": ["run", "-i", "--rm", "--network", "host", "mcr.microsoft.com/playwright/mcp", "--headless", "--browser", "chromium"]
    }
  }
}
```

> If `.mcp.json` already exists, merge this `playwright` entry into the existing `mcpServers` object — do not overwrite other servers. Use the exact image/flags B0 proved.

- [ ] **Step 2: Verify the server is recognized**

Run:
```sh
docker run --rm -v "$(cygpath -w "$PWD")":/w -w /w mikefarah/yq -p=json '.mcpServers.playwright.command' .mcp.json
```
Expected: prints `docker` (entry well-formed). (Live MCP handshake was proven in B0 Step 3.)

- [ ] **Step 3: Commit**

```sh
git add .mcp.json
git commit -m "feat(mcp): add Playwright MCP server for Tier-1 visual acceptance"
```

---

### Task B2: Author the `release-visual-validator` agent

**Files:**
- Create: `.claude/agents/release-visual-validator.md`

**Interfaces:**
- Consumes: the Playwright MCP server (`playwright`); the `release-visual-acceptance` skill (Task B3).
- Produces: an agent type `release-visual-validator` selectable via the Agent tool.

- [ ] **Step 1: Write the agent definition**

Front-matter `name`, `description`, `model: opus`. Body must encode the behavior contract (mirror existing agents' Chinese-primary style, e.g. `experience-analyst.md`):

```markdown
---
name: release-visual-validator
description: 发版前「真人体验官」——代入指定人物、无全局视角、不预知操作，经 Playwright MCP 在真实浏览器里摸索，逐步截图判断「以这个人物，我的事办顺了吗、有无反人类设计」。恶性缺陷出 NO-GO 挡发版,小建议汇报 Main Session。区别于纯静态的 experience-analyst。
model: opus
---

# release-visual-validator — 发版前真人体验官

## 核心角色
你**代入一个被指派的人物**，用真实浏览器（经 Playwright MCP）去用这个产品。你**只**拿到：
我是谁、我想办成什么事、我自己的账号、入口 URL。**你没有 UI 地图、不知道该点哪里、
不碰任何代码/架构/实现**。你像第一次使用的真人——看到什么才动什么。

## 铁律
- **无全局视角**：除人物设定与目标外，对系统一无所知。不读前端代码/路由/spec 来"作弊"找路。
- **不预知操作**：每一步先看当前页面快照+截图，再决定下一步，绝不凭记忆/先验直接操作。
- **只判体验，不判实现**：背后接没接通、代码好不好、性能如何——一律不关心，那是别人的事。
- **唯一的问题**：以我这个人物，我的事办顺了吗？哪里反人类 / 看不懂 / 走进死胡同 / 让我犹豫？

## 作业流程
1. 读 `release-visual-acceptance` skill（方法论、人物集、判级与路由口径）。
2. 对指派的人物：用 MCP 打开入口→逐步摸索去达成目标→**每一步截图**落 `_workspace/visual-acceptance/<run>/`。
3. 边走边记：我想干啥→我看到啥→我做了啥→结果如何→我的感受（顺/困惑/受阻）。
4. 走完或受阻后，按"反馈分级"给结论。

## 反馈分级与路由（关键）
- **恶性缺陷**（挡住人物办成核心任务、或界面烂到不可用）→ 该人物判 **NO-GO**，写明复现与证据截图。**挡发版**。
- **体验小建议**（papercut、可优化但不致命）→ 归入"给 Main Session 的建议"，**不挡发版**。
- 每条写成「人物想做 X → 实际遇到 Y → 期望 Z」，附截图引用，severity 按对人物任务的阻断程度（沿用 `UX-` 前缀 finding schema）。

## 产出
写 `_workspace/visual-acceptance/<run>/report.md`：逐人物叙事 + 截图引用 + `UX-` findings +
**整体 GO / NO-GO**（任一人物有恶性缺陷即 NO-GO）+ 一节"给 Main Session 的非阻断建议"。
完成后把 GO/NO-GO 与 top 问题回报调用方。

## 错误处理
- 摸索中找不到通往目标的路 → 这**本身就是 finding**（可发现性差/反人类），按对人物的阻断定级，不要去读代码找捷径。
- MCP/浏览器异常（非产品问题）→ 重试一次；仍失败则如实报"环境受阻、未能完成体验"，不臆断 GO。
```

- [ ] **Step 2: Verify the agent is registered**

Run:
```sh
docker run --rm -v "$(cygpath -w "$PWD")":/w -w /w mikefarah/yq --front-matter=extract '.name, .model' .claude/agents/release-visual-validator.md
```
Expected: prints `release-visual-validator` and `opus`.

- [ ] **Step 3: Commit**

```sh
git add .claude/agents/release-visual-validator.md
git commit -m "feat(agent): add release-visual-validator (Tier-1 visual experience officer)"
```

---

### Task B3: Author the `release-visual-acceptance` skill

**Files:**
- Create: `.claude/skills/release-visual-acceptance/SKILL.md`

**Interfaces:**
- Consumes: read by `release-visual-validator`; references the persona set + MCP config from B0.
- Produces: the methodology the agent follows.

- [ ] **Step 1: Write the skill**

```markdown
---
name: release-visual-acceptance
description: 发版前视觉验收方法论——代入人物、无预知、用 Playwright MCP 真浏览器摸索、逐步截图、判反人类设计、按"恶性缺陷挡发版 / 小建议汇报 Main Session"分级路由。release-visual-validator 使用。当需要"发版前体验走查、真人视角验收、视觉探索、UX 可用性把关"时使用。
---

# release-visual-acceptance — 发版前真人体验验收方法

把 Claude 代入真实使用者，用真浏览器（Playwright MCP）摸索产品，判断「以这个人物，我的事
办得顺不顺、有没有反人类的设计」。这是发版前 Tier-1（本地、人工触发）；与 Tier-2（CI 确定性
Playwright 功能门禁）**完全解耦**——Tier-1 不写/不喂 CI 脚本，只产出体验结论。

## 为何这样验（原则）
确定性脚本能验"功能对不对"，但验不了"用起来反不反人类"。把 Claude 当一个**无预知的真人**
放进界面，能抓住脚本抓不到的东西：路径绕、文案看不懂、关键入口藏得深、流程走到死胡同。
**只看结果与体验，不看实现。**

## 前置
- 栈已起（`docker compose up -d`，账号密码见 `docs/demo-accounts.md`，统一 `admin123`）。
- Playwright MCP 已按 [B0 spike 笔记] 配好（`docs/superpowers/notes/2026-06-26-playwright-mcp-spike.md`）。

## v1 人物集（每个只给"我是谁+目标+账号+URL"，绝不给步骤）
| 人物 | 账号 | 目标 |
|------|------|------|
| 家长 | guardian-kg1 / admin123 | 登录后想知道孩子今天在园里有没有异常/告警，并查看相关通知 |
| 教师 | teacher-kg1 / admin123 | 想查看本班的检测事件，并对其中一个做复核 |
| 园长 | director-kg1 / admin123 | 想了解本园概况、看待审批/管理项 |
| 超管 | admin / admin123 | 想跨园查看平台层面的概况 |

> 人物基于 seed 既有数据探索；真实用户不会自己注入事件（注入的因果验证归 Tier-2）。

## 走查手法
1. 用 MCP 打开 URL → 取页面快照+截图 → **看到什么才动什么** → 每步截图落 `_workspace/visual-acceptance/<run>/NN-*.png`。
2. 全程以人物口吻记录：我想干啥 / 我看到啥 / 我做了啥 / 结果 / 我的感受。
3. 达成目标 = 该人物 happy path 通；中途卡住/绕远/困惑 = 记 finding。

## 判级与路由
- **恶性缺陷**（挡住核心任务 / 不可用）→ 人物 NO-GO → **挡发版**（写复现+证据截图）。
- **体验小建议**（papercut）→ 列入"给 Main Session 的非阻断建议"。
- finding 写法：「人物想做 X → 实际 Y → 期望 Z」+ 截图引用 + severity（按对人物任务的阻断度）+ `UX-` 前缀。

## 产出
`_workspace/visual-acceptance/<run>/report.md`：逐人物叙事 + 截图 + `UX-` findings +
**整体 GO/NO-GO**（任一人物恶性缺陷即 NO-GO）+ 非阻断建议清单。

## 覆盖与局限
- 人物主观判断、非确定性；价值在抓反人类设计，不替代 Tier-2 功能正确性。
- 外部 PUSH/SMS 不在范围（无真实投递凭据），闭环终点取站内通知。
- v1 仅 4 人物；其余角色任务后续扩。
```

- [ ] **Step 2: Verify the skill front-matter**

Run:
```sh
docker run --rm -v "$(cygpath -w "$PWD")":/w -w /w mikefarah/yq --front-matter=extract '.name' .claude/skills/release-visual-acceptance/SKILL.md
```
Expected: prints `release-visual-acceptance`.

- [ ] **Step 3: Commit**

```sh
git add .claude/skills/release-visual-acceptance/SKILL.md
git commit -m "feat(skill): add release-visual-acceptance methodology (Tier-1)"
```

---

### Task B4: Tier-1 smoke run (guardian persona)

**Files:** none (verification only; produces gitignored `_workspace/` output)

- [ ] **Step 1: Run one persona end to end**

Dispatch the `release-visual-validator` agent for the **guardian** persona only (goal: "看看孩子今天有没有异常告警/通知"). Confirm it:
1. drives the real browser via MCP (login modal → fill → submit),
2. saves step screenshots under `_workspace/visual-acceptance/<run>/`,
3. produces `report.md` with a per-step narrative, any `UX-` findings, a GO/NO-GO, and a non-blocking-suggestions section.

Expected: a coherent persona report + screenshots. (Given the just-fixed login, expect GO unless a genuine UX issue surfaces.)

- [ ] **Step 2: Sanity — confirm the gate can say NO-GO**

Temporarily stop the frontend (`docker compose stop frontend`), re-run the guardian persona, confirm the agent reports it could not complete the task (env-blocked / NO-GO rather than a false GO). Then `docker compose start frontend`.

- [ ] **Step 3: No commit** (verification only; `_workspace/` is gitignored — see Task C1).

---

## Part C — Shared wiring

### Task C1: `.gitignore` + `CLAUDE.md` changelog

**Files:**
- Modify: `.gitignore` (repo root)
- Modify: `CLAUDE.md` (the Harness changelog table)

- [ ] **Step 1: Ensure `_workspace/visual-acceptance/` is ignored**

Check first:
```sh
git check-ignore _workspace/visual-acceptance/x.png && echo "already ignored" || echo "needs entry"
```
If "needs entry", append to `.gitignore`:
```gitignore

# Tier-1 visual acceptance local artifacts (screenshots/reports)
_workspace/visual-acceptance/
```

- [ ] **Step 2: Add a CLAUDE.md changelog row**

Append to the `变更历史` table in `CLAUDE.md`:
```markdown
| 2026-06-26 | 新增发版前双层验收:release-visual-validator(第 9 个 agent)+ release-visual-acceptance skill(Tier-1 真人体验官,Playwright MCP)+ 仓库根 e2e/ 确定性 Playwright(Tier-2,release.yml 硬门禁) | 新 agent/skill + e2e/ + release.yml + .mcp.json | 发版前需真人体验把关 + 功能硬门禁,两层解耦 |
```

- [ ] **Step 3: Commit**

```sh
git add .gitignore CLAUDE.md
git commit -m "chore: ignore visual-acceptance artifacts + record release-acceptance in CLAUDE.md changelog"
```

---

## Self-Review

**Spec coverage** (design §§ → tasks):
- §3 new artifacts: agent (B2), skill (B3), `.mcp.json` (B1), `e2e/` suite (A1–A3), CI step (A4) ✓
- §4 Tier-1 behavior contract (no global view / no pre-knowledge / persona routing) → B2 + B3 ✓; driving mechanism (MCP) → B0 + B1 ✓; persona set → B3 ✓; outputs/gitignore → B4 + C1 ✓
- §5 Tier-2 (code-authored, decoupled, in-app endpoint, CI hard gate, retries) → A1–A4 ✓
- §6 pipeline placement → A4 Step 4 (workflow_dispatch) + B4 ✓
- §7 misc (opus model, gitignore, .mcp.json, CLAUDE.md changelog) → B2/B1/C1 ✓
- §8 limits (external push out of scope; in-app endpoint) → encoded in A3/B3 ✓

**Placeholder scan:** Injection payload (A3 S2) and persona-trigger outcome (A3 S1) are explicit *discovery* steps that read named files, not vague TODOs. Task B0/B1 use the spike's verified command rather than a guessed one — intentional, since local MCP networking is genuinely environment-dependent.

**Type consistency:** `loginViaModal(page, loginId, password)` defined in A2, consumed in A3 ✓. `injectDetectionEvent(request, opts) → {dedupKey, eventType}` defined A3 S2, consumed A3 S3 ✓. Env names (`BASE_URL`, `API_BASE_URL`, `AI_SERVICE_TOKEN`, `RUN_ID`) consistent across config (A1), helper (A3), CI (A4) ✓.

**Known risk:** Local container browser ↔ host `:8080` origin reachability is the one genuine unknown; Task B0 front-loads it as a gating spike before any Tier-1 authoring depends on it. CI Tier-2 sidesteps it entirely by running Playwright natively on the runner.
