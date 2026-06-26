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
