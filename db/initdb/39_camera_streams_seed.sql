DELETE FROM "camera_streams";

-- Demo streams carry no credentials.
-- Real cameras are configured via POST/PUT (which performs true AES-256-GCM encryption
-- through CameraStreamService → AesGcmCryptoUtil). These seed rows exist solely to
-- populate a realistic demo environment and have no valid ciphertext to decrypt.
--
-- Effect on runtime behavior:
--   • GET /api/v1/internal/streams/{id}/credentials  →  streamPassword = null
--   • build_stream_url() in the AI client falls back to source_url (open RTSP)
--
-- OQ-4=C (ADR-0026): seed rows use NULL for all five credential columns; fake
-- placeholder strings were removed because AesGcmCryptoUtil.decrypt() would throw on them.
INSERT INTO "camera_streams" ("stream_id","kindergarten_id", "camera_id", "stream_type", "source_url", "stream_user", "stream_password_ciphertext", "stream_password_iv", "stream_password_key_version", "source_protocol", "playback_url", "playback_protocol", "fps", "resolution", "is_primary", "enabled", "status", "credential_updated_at", "created_at", "updated_at") VALUES (1, 1, 1, 'MAIN', 'rtsp://10.10.1.1:554/live/main', NULL, NULL, NULL, NULL, 'RTSP', 'https://stream.example.com/hls/kg1/cam1/main.m3u8', 'HTTPS', 30, '1920x1080', true, true, 'ACTIVE', NULL, now(), now());
INSERT INTO "camera_streams" ("stream_id","kindergarten_id", "camera_id", "stream_type", "source_url", "stream_user", "stream_password_ciphertext", "stream_password_iv", "stream_password_key_version", "source_protocol", "playback_url", "playback_protocol", "fps", "resolution", "is_primary", "enabled", "status", "credential_updated_at", "created_at", "updated_at") VALUES (2, 2, 2, 'MAIN', 'rtsp://10.10.2.1:554/live/main', NULL, NULL, NULL, NULL, 'RTSP', 'https://stream.example.com/hls/kg2/cam2/main.m3u8', 'HTTPS', 30, '1920x1080', true, true, 'ACTIVE', NULL, now(), now());
INSERT INTO "camera_streams" ("stream_id","kindergarten_id", "camera_id", "stream_type", "source_url", "stream_user", "stream_password_ciphertext", "stream_password_iv", "stream_password_key_version", "source_protocol", "playback_url", "playback_protocol", "fps", "resolution", "is_primary", "enabled", "status", "credential_updated_at", "created_at", "updated_at") VALUES (3, 3, 3, 'MAIN', 'rtsp://10.10.3.1:554/live/main', NULL, NULL, NULL, NULL, 'RTSP', 'https://stream.example.com/hls/kg3/cam3/main.m3u8', 'HTTPS', 30, '1920x1080', true, true, 'ACTIVE', NULL, now(), now());
