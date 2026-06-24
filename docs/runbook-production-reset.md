# Runbook: production reset to a clean, seed-free cold start

> **DESTRUCTIVE. Maintainer-executed in the deployment environment only.**
> This wipes the production PostgreSQL volume. Every step requires explicit,
> per-step maintainer approval. This document does not execute anything; it is
> the operating procedure.

## Why

Earlier production images loaded the full `db/initdb` demo seed (including
`admin`/`admin123` and all demo accounts) into the persistent `postgres_data`
volume. Even though production now builds `db/Dockerfile.prod` (vanilla
PostgreSQL, **no initdb seed** — schema is created by Flyway), the residual
seeded accounts survive in the old volume, so public robots can still try
`admin`/`admin123`. The fix is a clean cold start: wipe the volume, let Flyway
build a schema-only database, and bootstrap exactly one maintainer SUPERADMIN
from deploy-time secrets (login id **not** `admin`).

After this reset, a robot scanning `admin`/`admin123` (or any demo account) gets
`401` because no such account exists.

## Preconditions

- You are the maintainer with deploy access to the host.
- Production stack is the prod track: `docker compose -f docker-compose.yml -f docker-compose.prod.yml ...`
  (`db` built from `db/Dockerfile.prod`, no seed; Caddy edge TLS; `SESSION_COOKIE_SECURE=true`).
- You have, or can generate, a **strong** bootstrap password and a non-`admin`
  login id, kept out-of-band (a secret manager / password vault — never in git).

## Procedure (each step requires maintainer approval)

1. **Backup the current volume (if any data must be retained).**
   Take a `pg_dump` or a volume snapshot before destroying anything. Keep the
   backup until the reset is verified.

   ```sh
   docker compose -f docker-compose.yml -f docker-compose.prod.yml exec db \
     pg_dump -U kids_user kids_postgres_db > backup-$(date +%F).sql
   ```

2. **Stop the stack and delete the PostgreSQL volume.**
   This is the destructive step — it removes all residual seeded data.

   ```sh
   docker compose -f docker-compose.yml -f docker-compose.prod.yml down
   docker volume rm <project>_postgres_data
   ```

   (Find the exact volume name with `docker volume ls`; it is the
   `postgres_data` volume for this compose project.)

3. **Confirm the seed-free production image.**
   Verify `db` builds from `db/Dockerfile.prod` (no `COPY initdb/`), so the fresh
   volume gets a schema-only database. `docker compose ... config` shows the
   effective build context.

4. **Set the bootstrap env + production gates.**
   For this first cold start only, export the bootstrap credential alongside the
   existing required production secrets:

   ```sh
   export BOOTSTRAP_ADMIN_LOGIN_ID=<non-admin-login-id>
   export BOOTSTRAP_ADMIN_PASSWORD=<strong-password>     # never 'admin123', never in git
   export RRN_HASH_PEPPER=...                             # existing prod gate
   export CAMERA_STREAM_AES_KEY_V1=...                    # existing prod gate
   export AI_SERVICE_TOKEN=...                            # existing prod gate
   # SESSION_COOKIE_SECURE=true is already set by docker-compose.prod.yml
   ```

   The login id **must not** be `admin` (the runner refuses it). The runner
   creates exactly one `ACTIVE` `SUPERADMIN` only when the `users` table is empty.

5. **Start the stack.**
   On a fresh empty database, Flyway runs `V1` and builds the full schema, then
   the `AdminBootstrapRunner` creates the single bootstrap SUPERADMIN.

   ```sh
   DOMAIN=app.example.com ACME_EMAIL=ops@example.com \
     docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
   ```

6. **Log in and verify.**
   - Sign in with `BOOTSTRAP_ADMIN_LOGIN_ID` / `BOOTSTRAP_ADMIN_PASSWORD`.
   - Confirm `admin`/`admin123` and all demo accounts now fail with `401`.
   - Confirm login throttling: repeated failures for one identifier return `429`.

7. **Rotate the bootstrap credential out-of-band and clear the env.**
   There is no schema-backed forced password change (zero-schema change), so
   rotate manually: change the bootstrap account's password through the normal
   flow or via DBA, store it in the secret manager, then **remove**
   `BOOTSTRAP_ADMIN_LOGIN_ID` / `BOOTSTRAP_ADMIN_PASSWORD` from the deploy env and
   restart so the secret no longer lives in process env. The runner is idempotent
   (users now exist), so a later restart with the env still set would be a no-op,
   but clearing it removes the residual secret.

## Rollback

If the reset fails, restore from the step-1 backup into a fresh volume and
restart the stack. The bootstrap and throttling behaviors are additive: clearing
the bootstrap env and the `LOGIN_THROTTLE_*` thresholds softly disables them.
