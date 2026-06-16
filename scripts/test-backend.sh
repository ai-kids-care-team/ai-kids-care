#!/usr/bin/env bash
# Local backend test/compile loop — runs Gradle inside a container against the host
# Docker daemon (Docker-out-of-Docker), so NO local Java/JDK install is needed.
#
# Why this works:
#   - Gradle runs in gradle:8.5-jdk21-alpine (same image as backend/Dockerfile).
#   - Integration tests use Testcontainers; the host Docker socket is mounted so they
#     can launch sibling Postgres/Redis containers. TESTCONTAINERS_HOST_OVERRIDE points
#     them at host.docker.internal so the gradle container can connect.
#   - BaseIntegrationTest uses withCopyFileToContainer (not a host bind mount) for
#     db/initdb, so the schema loads under DooD too.
#   - A named volume caches the Gradle deps, so only the first run is slow.
#
# Usage (run from anywhere; resolves the repo root itself):
#   scripts/test-backend.sh                          # full backend test suite
#   scripts/test-backend.sh '*NotificationRead*'     # only matching test classes
#   scripts/test-backend.sh '*Notification* *Guardian*'   # space-separated patterns
#   scripts/test-backend.sh --compile                # compile main+test only (fastest, no DB)
#
# Requires: Docker (Desktop). On Windows, run via Git Bash.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && { pwd -W 2>/dev/null || pwd; })"
IMAGE=gradle:8.5-jdk21-alpine

common=(
  --rm
  -v "${REPO}:/repo" -w /repo/backend
  -e GRADLE_USER_HOME=/gradle-cache -v kids-gradle-cache:/gradle-cache
)

if [ "${1:-}" = "--compile" ]; then
  MSYS_NO_PATHCONV=1 docker run "${common[@]}" "$IMAGE" gradle compileTestJava --no-daemon
  exit $?
fi

test_args=( gradle test --no-daemon )
if [ -n "${1:-}" ]; then
  for pat in $1; do test_args+=( --tests "$pat" ); done
fi

MSYS_NO_PATHCONV=1 docker run "${common[@]}" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --add-host host.docker.internal:host-gateway \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  "$IMAGE" "${test_args[@]}"
rc=$?

# Ryuk (the Testcontainers reaper) is disabled under DooD, so reap the Postgres/Redis
# containers this run started, to avoid them accumulating.
docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f >/dev/null 2>&1 || true

exit $rc
