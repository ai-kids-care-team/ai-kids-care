import { APIRequestContext } from '@playwright/test';

/*
 * DISCOVERY FINDINGS — confirmed 2026-06-26 against real source files:
 *
 * ── Session endpoint ────────────────────────────────────────────────────────
 * POST /api/v1/internal/detection-sessions
 *   Payload  : { streamId: number, modelId: number }
 *   Auth     : Authorization: Bearer <AI_SERVICE_TOKEN>  (no CSRF token needed)
 *   Returns  : { sessionId: number }
 *   Sources  : ai/src/ai_app/utils/backend_ingest.py::create_session
 *              backend/.../internal/DetectionSessionIngestController.java
 *              backend/.../service/DetectionIngestService (body inferred from Python client)
 *
 * ── Event endpoint ──────────────────────────────────────────────────────────
 * POST /api/v1/internal/detection-events
 *   Payload  :
 *     sessionId  : number           — from session step
 *     eventType  : string           — EventTypeEnum, e.g. "ASSAULT"
 *     severity   : number           — 1–5 bucket; confidence 0.94 → severity 5
 *     confidence : number           — 0..1
 *     startTime  : string           — ISO-8601 OffsetDateTime (e.g. "2026-06-26T12:00:00Z")
 *     endTime    : string           — same instant = single-frame demo window
 *     dedupKey   : string           — "{streamId}-{ms_epoch}" unique per run
 *   Returns  : { eventId: number, duplicate: boolean }
 *   Sources  : ai/src/ai_app/utils/backend_ingest.py::submit_event
 *              backend/.../internal/DetectionEventIngestRequest.java
 *              (field names are exact Java record components)
 *
 * ── Severity mapping (from backend_ingest.py::severity_from_confidence) ─────
 *   confidence < 0.30 → 1  |  0.30–0.50 → 2  |  0.50–0.70 → 3
 *   0.70–0.85 → 4  |  ≥ 0.85 → 5
 *   default confidence 0.94 → severity 5
 *
 * ── Review outcome that deterministically notifies guardians ────────────────
 * Use: ESCALATED ("에스컬레이션")
 * Reason: GuardianNotificationService.java (notifyOnReview):
 *   boolean shouldNotify = resultStatus == EventStatusEnum.ESCALATED
 *       || (resultStatus == EventStatusEnum.RESOLVED && Boolean.TRUE.equals(notifyGuardians));
 * The teacher's review UI (DetectionEventsDashboard.tsx::handleReview) does NOT
 * send notifyGuardians — it omits it entirely. So RESOLVED never triggers a
 * guardian notification from the UI. ESCALATED always notifies.
 *
 * ── Guardian chain for stream_id=1 (verified via seed files) ────────────────
 *   39_camera_streams_seed.sql   : stream_id=1 → kindergarten_id=1, camera_id=1
 *   38_room_camera_assignments   : camera_id=1 → room_id=1 (end_at=null → active)
 *   36_class_room_assignments    : room_id=1 → class_id=1 (ACTIVE, end_at=null)
 *   34_child_class_assignments   : class_id=1 → child_id=1 (ACTIVE, end_date=null)
 *   37_child_guardian_relationships: child_id=1 → guardian_id=1 (end_date=null)
 *   29_guardians_seed.sql        : guardian_id=1 → user_id=121
 *   21_users_seed.sql            : user_id=121 → login_id "guardian-kg1"
 *
 * Conclusion: escalating a stream_id=1 event guarantees guardian-kg1 receives
 * a PUSH notification (title "안전 알림") via the GuardianNotificationService.
 */

const API = process.env.API_BASE_URL ?? 'http://localhost:8080';
const TOKEN = process.env.AI_SERVICE_TOKEN ?? '';

export interface InjectResult {
  dedupKey: string;
  eventType: string;
  eventId: number;
}

/**
 * Replicates ai/scripts/inject_demo_event.py via the internal ingest endpoints
 * (Bearer AI_SERVICE_TOKEN, CSRF-exempt). Returns identifiers to locate the
 * event in the teacher dashboard.
 *
 * Field names verified against:
 *   - ai/src/ai_app/utils/backend_ingest.py (Python reference client)
 *   - backend/.../internal/DetectionEventIngestRequest.java (exact record fields)
 */
export async function injectDetectionEvent(
  request: APIRequestContext,
  opts: {
    streamId?: number;
    modelId?: number;
    eventType?: string;
    confidence?: number;
  } = {},
): Promise<InjectResult> {
  const { streamId = 1, modelId = 1, eventType = 'ASSAULT', confidence = 0.94 } = opts;
  const headers = {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json',
  };

  // 1) Create detection session.
  //    POST {streamId, modelId} → {sessionId}
  const sessionResp = await request.post(`${API}/api/v1/internal/detection-sessions`, {
    headers,
    data: { streamId, modelId },
  });
  if (!sessionResp.ok()) {
    throw new Error(`session ingest failed: ${sessionResp.status()} ${await sessionResp.text()}`);
  }
  const sessionId: number = (await sessionResp.json()).sessionId;

  // 2) Submit detection event.
  //    dedupKey: "{streamId}-{ms_epoch}" — millisecond precision ensures uniqueness per run.
  //    startTime/endTime: same UTC instant (single-frame demo window); "Z" is a valid OffsetDateTime offset.
  //    severity: derived from confidence via the same bucket rule as severity_from_confidence().
  const now = new Date().toISOString();
  const dedupKey = `${streamId}-${Date.now()}`;
  const severity =
    confidence >= 0.85 ? 5
    : confidence >= 0.70 ? 4
    : confidence >= 0.50 ? 3
    : confidence >= 0.30 ? 2
    : 1;

  const eventResp = await request.post(`${API}/api/v1/internal/detection-events`, {
    headers,
    data: {
      sessionId,
      eventType,
      severity,
      confidence,
      startTime: now,
      endTime: now,
      dedupKey,
    },
  });
  if (!eventResp.ok()) {
    throw new Error(`event ingest failed: ${eventResp.status()} ${await eventResp.text()}`);
  }
  const body = await eventResp.json();
  return { dedupKey, eventType, eventId: body.eventId };
}
