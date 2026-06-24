-- ADR-0013: retire the platform dictionary tables.
--
-- `menu` and `common_codes` were reference/lookup tables. Their roles are replaced by:
--   * `menu`         -> front-end static menu config (frontend/src/config/menu.ts)
--   * `common_codes` -> backend enum metadata endpoint GET /api/v1/enums/{name}
--                       (backed by com.ai_kids_care.v1.type.* enums) + front-end i18n labels.
--
-- These tables only ever existed in db/initdb (not in a Flyway migration), so on a fresh
-- Flyway-only production database they are already absent and this DROP is a no-op. On an
-- initdb-seeded database (dev / integration-test container) it removes the now-retired tables.
-- Business tables store status/type as code strings/enums, never as FKs to these dictionaries,
-- so the DROP has no impact on business data. Idempotent (IF EXISTS).

DROP TABLE IF EXISTS menu;
DROP TABLE IF EXISTS common_codes;
