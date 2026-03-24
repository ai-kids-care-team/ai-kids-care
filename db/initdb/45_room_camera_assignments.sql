INSERT INTO room_camera_assignments (
    kindergarten_id, camera_id, room_id, start_at, end_at, created_at, updated_at
)
SELECT 
    cam.kindergarten_id,
    cam.camera_id,
    r.room_id,
    CURRENT_TIMESTAMP,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM cctv_cameras cam
JOIN LATERAL (
    SELECT room_id FROM rooms 
    WHERE kindergarten_id = cam.kindergarten_id 
    ORDER BY RANDOM() LIMIT 1
) r ON true;