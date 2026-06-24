# Demo accounts (demo / CI / local only — never in production)

These accounts come from the `db/initdb/` demo seed (loaded only into the
dev/test/CI database image, `db/Dockerfile`). A **production** database is
schema-only (Flyway, `Dockerfile.prod`) with **no seed**, so none of these
accounts exist there. Do not rely on, document, or attempt to use these in a
production deployment — see `harden-auth-bootstrap-and-demo` and the
data-platform `Production does not depend on seed` requirement.

> All demo accounts share the password **`admin123`**.

## Accounts

| login_id | Password | Role | Kindergarten (tenant) | Demo step |
|---|---|---|---|---|
| `admin` | `admin123` | SUPERADMIN | — (PLATFORM) | Platform admin: review all tenants, platform AI metadata. Test-anchor (`user_id=1`); demo/CI-only. |
| `director-kg1` | `admin123` | KINDERGARTEN_ADMIN | 하늘유치원 (kg1) | Tenant admin: dashboard, cameras/streams, detection review for kg1. |
| `teacher-kg1` | `admin123` | TEACHER | 하늘유치원 (kg1) | Teacher: classroom view, event review for kg1. |
| `guardian-kg1` | `admin123` | GUARDIAN | 하늘유치원 (kg1) | Guardian: receives the PUSH/SMS notification after a kg1 event is reviewed. |
| `director-kg2` | `admin123` | KINDERGARTEN_ADMIN | 바다유치원 (kg2) | Same as kg1 director, tenant kg2 (tenant-isolation demo). |
| `teacher-kg2` | `admin123` | TEACHER | 바다유치원 (kg2) | Teacher for kg2. |
| `guardian-kg2` | `admin123` | GUARDIAN | 바다유치원 (kg2) | Guardian for kg2. |
| `director-kg3` | `admin123` | KINDERGARTEN_ADMIN | 튼튼유치원 (kg3) | KINDERGARTEN_ADMIN for kg3. |
| `teacher-kg3` | `admin123` | TEACHER | 튼튼유치원 (kg3) | Teacher for kg3. |
| `guardian-kg3` | `admin123` | GUARDIAN | 튼튼유치원 (kg3) | Guardian for kg3. |

## End-to-end demonstration flow

1. **Login** — sign in as `director-kg1` / `admin123` (or `teacher-kg1`) to land
   on the kindergarten 1 realtime detection dashboard.
2. **Realtime event** — with the AI inference service not running, inject a
   detection event manually so it flows through the exact internal ingest path a
   real AI would use:

   ```sh
   AI_SERVICE_TOKEN=<demo-backend-token> python ai/scripts/inject_demo_event.py \
       --backend-url http://localhost:8080 \
       --stream-id 1 --model-id 1 \
       --event-type ASSAULT --confidence 0.94
   ```

   The backend persists a detection session + event for kindergarten 1 and pushes
   it over SSE to the connected dashboard. See `ai/scripts/inject_demo_event.py`
   (DEMO ONLY — never point it at production; production has no demo stream/model).
3. **Review** — review/confirm the event in the dashboard.
4. **Guardian notification** — on confirmation the guardian (`guardian-kg1`)
   receives the PUSH (and/or SMS) notification, completing the loop.

## Frontend login hints

The login page can show these demo credentials as on-screen hints, gated by
`NEXT_PUBLIC_SHOW_DEMO_HINTS=true` (see `frontend/.env.example`). This flag is
**unset/false in production**, so the production login UI shows no credential
hints. Enable it only in a demo/local environment.
