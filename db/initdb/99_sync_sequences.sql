BEGIN;

SELECT setval(pg_get_serial_sequence('"users"', 'user_id'),
              COALESCE(MAX("user_id"), 1),
              MAX("user_id") IS NOT NULL)
FROM "users";

SELECT setval(pg_get_serial_sequence('"kindergartens"', 'kindergarten_id'),
              COALESCE(MAX("kindergarten_id"), 1),
              MAX("kindergarten_id") IS NOT NULL)
FROM "kindergartens";

SELECT setval(pg_get_serial_sequence('"classes"', 'class_id'),
              COALESCE(MAX("class_id"), 1),
              MAX("class_id") IS NOT NULL)
FROM "classes";

SELECT setval(pg_get_serial_sequence('"children"', 'child_id'),
              COALESCE(MAX("child_id"), 1),
              MAX("child_id") IS NOT NULL)
FROM "children";

SELECT setval(pg_get_serial_sequence('"child_class_assignments"', 'assignment_id'),
              COALESCE(MAX("assignment_id"), 1),
              MAX("assignment_id") IS NOT NULL)
FROM "child_class_assignments";

SELECT setval(pg_get_serial_sequence('"teachers"', 'teacher_id'),
              COALESCE(MAX("teacher_id"), 1),
              MAX("teacher_id") IS NOT NULL)
FROM "teachers";

SELECT setval(pg_get_serial_sequence('"superadmins"', 'superadmin_id'),
              COALESCE(MAX("superadmin_id"), 1),
              MAX("superadmin_id") IS NOT NULL)
FROM "superadmins";

SELECT setval(pg_get_serial_sequence('"class_teacher_assignments"', 'assignment_id'),
              COALESCE(MAX("assignment_id"), 1),
              MAX("assignment_id") IS NOT NULL)
FROM "class_teacher_assignments";

SELECT setval(pg_get_serial_sequence('"rooms"', 'room_id'),
              COALESCE(MAX("room_id"), 1),
              MAX("room_id") IS NOT NULL)
FROM "rooms";

SELECT setval(pg_get_serial_sequence('"class_room_assignments"', 'assignment_id'),
              COALESCE(MAX("assignment_id"), 1),
              MAX("assignment_id") IS NOT NULL)
FROM "class_room_assignments";

SELECT setval(pg_get_serial_sequence('"cctv_cameras"', 'camera_id'),
              COALESCE(MAX("camera_id"), 1),
              MAX("camera_id") IS NOT NULL)
FROM "cctv_cameras";

SELECT setval(pg_get_serial_sequence('"room_camera_assignments"', 'assignment_id'),
              COALESCE(MAX("assignment_id"), 1),
              MAX("assignment_id") IS NOT NULL)
FROM "room_camera_assignments";

SELECT setval(pg_get_serial_sequence('"guardians"', 'guardian_id'),
              COALESCE(MAX("guardian_id"), 1),
              MAX("guardian_id") IS NOT NULL)
FROM "guardians";

SELECT setval(pg_get_serial_sequence('"user_kindergarten_memberships"', 'membership_id'),
              COALESCE(MAX("membership_id"), 1),
              MAX("membership_id") IS NOT NULL)
FROM "user_kindergarten_memberships";

SELECT setval(pg_get_serial_sequence('"user_role_assignments"', 'role_assignment_id'),
              COALESCE(MAX("role_assignment_id"), 1),
              MAX("role_assignment_id") IS NOT NULL)
FROM "user_role_assignments";

SELECT setval(pg_get_serial_sequence('"camera_streams"', 'stream_id'),
              COALESCE(MAX("stream_id"), 1),
              MAX("stream_id") IS NOT NULL)
FROM "camera_streams";

SELECT setval(pg_get_serial_sequence('"ai_models"', 'model_id'),
              COALESCE(MAX("model_id"), 1),
              MAX("model_id") IS NOT NULL)
FROM "ai_models";

SELECT setval(pg_get_serial_sequence('"detection_sessions"', 'session_id'),
              COALESCE(MAX("session_id"), 1),
              MAX("session_id") IS NOT NULL)
FROM "detection_sessions";

SELECT setval(pg_get_serial_sequence('"detection_events"', 'event_id'),
              COALESCE(MAX("event_id"), 1),
              MAX("event_id") IS NOT NULL)
FROM "detection_events";

SELECT setval(pg_get_serial_sequence('"event_reviews"', 'review_id'),
              COALESCE(MAX("review_id"), 1),
              MAX("review_id") IS NOT NULL)
FROM "event_reviews";

SELECT setval(pg_get_serial_sequence('"event_evidence_files"', 'evidence_id'),
              COALESCE(MAX("evidence_id"), 1),
              MAX("evidence_id") IS NOT NULL)
FROM "event_evidence_files";

SELECT setval(pg_get_serial_sequence('"device_tokens"', 'device_id'),
              COALESCE(MAX("device_id"), 1),
              MAX("device_id") IS NOT NULL)
FROM "device_tokens";

SELECT setval(pg_get_serial_sequence('"notification_rules"', 'rule_id'),
              COALESCE(MAX("rule_id"), 1),
              MAX("rule_id") IS NOT NULL)
FROM "notification_rules";

SELECT setval(pg_get_serial_sequence('"notifications"', 'notification_id'),
              COALESCE(MAX("notification_id"), 1),
              MAX("notification_id") IS NOT NULL)
FROM "notifications";

SELECT setval(pg_get_serial_sequence('"audit_logs"', 'audit_id'),
              COALESCE(MAX("audit_id"), 1),
              MAX("audit_id") IS NOT NULL)
FROM "audit_logs";

SELECT setval(pg_get_serial_sequence('"announcements"', 'id'),
              COALESCE(MAX("id"), 1),
              MAX("id") IS NOT NULL)
FROM "announcements";

COMMIT;