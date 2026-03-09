CREATE TABLE kindergarten (
  kindergarten_id bigint PRIMARY KEY,
  name varchar,
  address varchar,
  region_code varchar,
  code varchar,
  contact_name varchar,
  contact_phone varchar,
  contact_email varchar,
  status varchar,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE class_entity (
  class_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  name varchar,
  grade varchar,
  academic_year bigint,
  start_date date,
  end_date date,
  status varchar,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE child (
  child_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  class_id bigint,
  name varchar,
  child_no varchar,
  rrn_encrypted varchar,
  rrn_first6 varchar,
  birth_date date,
  gender varchar,
  address varchar,
  enroll_date date,
  leave_date date,
  status varchar,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE child_class_assignment (
  assignment_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  child_id bigint NOT NULL,
  class_id bigint NOT NULL,
  start_date date,
  end_date date,
  reason varchar,
  note varchar,
  status varchar,
  created_by_user_id bigint,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE teacher (
  teacher_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  user_id bigint NOT NULL,
  staff_no varchar,
  name varchar,
  gender varchar,
  phone varchar,
  email varchar,
  emergency_contact_name varchar,
  emergency_contact_phone varchar,
  rrn_encrypted varchar,
  rrn_first6 varchar,
  level varchar,
  start_date date,
  end_date date,
  status varchar,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE class_teacher_assignment (
  assignment_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  class_id bigint NOT NULL,
  teacher_id bigint NOT NULL,
  role varchar,
  start_date date,
  end_date date,
  reason varchar,
  note varchar,
  status varchar,
  created_by_user_id bigint,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE room (
  room_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  name varchar,
  room_code varchar,
  location_note varchar,
  room_type varchar,
  status varchar,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE class_room_assignment (
  assignment_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  class_id bigint NOT NULL,
  room_id bigint NOT NULL,
  start_timestamptz timestamptz NOT NULL,
  end_timestamptz timestamptz NOT NULL,
  purpose varchar,
  note varchar,
  status varchar,
  created_by_user_id bigint,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE cctv_camera (
  camera_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  serial_no varchar,
  camera_name varchar,
  model varchar,
  created_by_user_id varchar,
  status varchar,
  last_seen_at timestamptz,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE room_camera_assignment (
  assignment_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  camera_id bigint NOT NULL,
  room_id bigint NOT NULL,
  start_timestamptz timestamptz NOT NULL,
  end_timestamptz timestamptz,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE guardian (
  guardian_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  user_id bigint NOT NULL,
  name varchar,
  rrn_encrypted varchar,
  rrn_first6 varchar,
  gender varchar,
  phone varchar,
  email varchar,
  address varchar,
  status varchar,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE child_guardian_relationship (
  kindergarten_id bigint NOT NULL,
  child_id bigint NOT NULL,
  guardian_id bigint NOT NULL,
  relationship varchar,
  is_primary boolean,
  priority int,
  start_date date,
  end_date date,
  created_at timestamptz,
  updated_at timestamptz,
  PRIMARY KEY (kindergarten_id, child_id, guardian_id)
);

CREATE TABLE user_kindergarten_membership (
  membership_id bigint PRIMARY KEY,
  user_id bigint NOT NULL,
  kindergarten_id bigint NOT NULL,
  status varchar,
  joined_at timestamptz,
  left_at timestamptz,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE user_role_assignment (
  role_assignment_id bigint PRIMARY KEY,
  user_id bigint NOT NULL,
  role varchar,
  scope_type varchar,
  scope_id bigint,
  status varchar,
  granted_at timestamptz,
  granted_by_user_id bigint,
  revoked_at timestamptz
);

CREATE TABLE camera_stream (
  camera_id bigint PRIMARY KEY,
  stream_url varchar,
  stream_user varchar,
  stream_password_encrypted varchar,
  protocol varchar,
  fps int,
  resolution varchar,
  enabled boolean,
  updated_at timestamptz
);

CREATE TABLE ai_model (
  model_id bigint PRIMARY KEY,
  name varchar,
  version varchar,
  status varchar,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE detection_session (
  session_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  camera_id bigint NOT NULL,
  model_id bigint NOT NULL,
  started_at timestamptz,
  ended_at timestamptz,
  status varchar,
  avg_latency_ms int,
  inference_fps float
);

CREATE TABLE detection_event (
  event_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  camera_id bigint NOT NULL,
  room_id bigint NOT NULL,
  session_id bigint NOT NULL,
  event_type varchar,
  severity int,
  confidence float,
  detected_at timestamptz,
  start_time timestamptz,
  end_time timestamptz,
  status varchar,
  created_at timestamptz,
  updated_at timestamptz
);

CREATE TABLE event_review (
  review_id bigint PRIMARY KEY,
  event_id bigint NOT NULL,
  kindergarten_id bigint NOT NULL,
  user_id bigint NOT NULL,
  action varchar,
  result_status varchar,
  comment varchar,
  created_at timestamptz
);

CREATE TABLE event_evidence (
  evidence_id bigint PRIMARY KEY,
  event_id bigint NOT NULL,
  kindergarten_id bigint NOT NULL,
  type varchar,
  storage_uri varchar,
  mime_type varchar,
  created_at timestamptz,
  retention_until timestamptz,
  hold boolean,
  hash varchar
);

CREATE TABLE device_token (
  device_id bigint PRIMARY KEY,
  user_id bigint NOT NULL,
  platform varchar,
  push_token varchar,
  status varchar,
  last_seen_at timestamptz,
  created_at timestamptz
);

CREATE TABLE notification_rule (
  rule_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  user_id bigint NOT NULL,
  scope_type varchar,
  scope_id bigint,
  event_type varchar,
  min_severity int,
  quiet_hours_json varchar,
  enabled boolean,
  created_at timestamptz
);

CREATE TABLE notification (
  notification_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  event_id bigint NOT NULL,
  recipient_user_id bigint NOT NULL,
  channel varchar,
  title varchar,
  body varchar,
  status varchar,
  sent_at timestamptz,
  fail_reason varchar,
  retry_count int,
  created_at timestamptz
);

CREATE TABLE audit_log (
  audit_id bigint PRIMARY KEY,
  kindergarten_id bigint NOT NULL,
  user_id bigint NOT NULL,
  action varchar,
  resource_type varchar,
  resource_id bigint,
  ip varchar,
  user_agent varchar,
  created_at timestamptz
);

CREATE UNIQUE INDEX uq_class_kg_classid ON class_entity (kindergarten_id, class_id);

CREATE UNIQUE INDEX uq_child_kg_childid ON child (kindergarten_id, child_id);

CREATE UNIQUE INDEX uq_child_kg_childno ON child (kindergarten_id, child_no);

CREATE INDEX idx_child_kg_classid ON child (kindergarten_id, class_id);

CREATE INDEX idx_cca_child_time ON child_class_assignment (kindergarten_id, child_id, start_date, end_date);

CREATE INDEX idx_cca_class_time ON child_class_assignment (kindergarten_id, class_id, start_date, end_date);

CREATE UNIQUE INDEX uq_teacher_kg_teacherid ON teacher (kindergarten_id, teacher_id);

CREATE UNIQUE INDEX uq_teacher_kg_staffno ON teacher (kindergarten_id, staff_no);

CREATE INDEX idx_cta_class_time ON class_teacher_assignment (kindergarten_id, class_id, start_date, end_date);

CREATE INDEX idx_cta_teacher_time ON class_teacher_assignment (kindergarten_id, teacher_id, start_date, end_date);

CREATE UNIQUE INDEX uq_room_kg_roomid ON room (kindergarten_id, room_id);

CREATE UNIQUE INDEX uq_room_kg_roomcode ON room (kindergarten_id, room_code);

CREATE INDEX idx_cra_room_time ON class_room_assignment (kindergarten_id, room_id, start_timestamptz, end_timestamptz);

CREATE INDEX idx_cra_class_time ON class_room_assignment (kindergarten_id, class_id, start_timestamptz, end_timestamptz);

CREATE UNIQUE INDEX uq_camera_kg_cameraid ON cctv_camera (kindergarten_id, camera_id);

CREATE UNIQUE INDEX uq_camera_kg_serialno ON cctv_camera (kindergarten_id, serial_no);

CREATE INDEX idx_rca_camera_time ON room_camera_assignment (kindergarten_id, camera_id, start_timestamptz, end_timestamptz);

CREATE INDEX idx_rca_room_time ON room_camera_assignment (kindergarten_id, room_id, start_timestamptz, end_timestamptz);

CREATE UNIQUE INDEX uq_guardian_kg_guardianid ON guardian (kindergarten_id, guardian_id);

CREATE INDEX idx_cgr_child ON child_guardian_relationship (kindergarten_id, child_id);

CREATE INDEX idx_cgr_guardian ON child_guardian_relationship (kindergarten_id, guardian_id);

CREATE UNIQUE INDEX uq_session_kg_sessionid ON detection_session (kindergarten_id, session_id);

CREATE UNIQUE INDEX uq_event_kg_eventid ON detection_event (kindergarten_id, event_id);

CREATE INDEX idx_review_event_time ON event_review (kindergarten_id, event_id, created_at);

CREATE INDEX idx_evidence_event_time ON event_evidence (kindergarten_id, event_id, created_at);

CREATE INDEX idx_rule_owner ON notification_rule (kindergarten_id, user_id);

CREATE INDEX idx_rule_enabled ON notification_rule (kindergarten_id, enabled);

CREATE INDEX idx_notif_recipient_time ON notification (kindergarten_id, recipient_user_id, created_at);

CREATE INDEX idx_notif_event ON notification (kindergarten_id, event_id);

COMMENT ON COLUMN kindergarten.kindergarten_id IS '유치원 ID';

COMMENT ON COLUMN kindergarten.name IS '유치원명';

COMMENT ON COLUMN kindergarten.address IS '주소';

COMMENT ON COLUMN kindergarten.region_code IS '지역 코드(행정구역/내부 코드)';

COMMENT ON COLUMN kindergarten.code IS '유치원 코드(내부/기관 식별용)';

COMMENT ON COLUMN kindergarten.contact_name IS '담당자 이름';

COMMENT ON COLUMN kindergarten.contact_phone IS '담당자 전화번호';

COMMENT ON COLUMN kindergarten.contact_email IS '담당자 이메일';

COMMENT ON COLUMN kindergarten.status IS '상태(운영/중지/폐원 등)';

COMMENT ON COLUMN kindergarten.created_at IS '생성 일시';

COMMENT ON COLUMN kindergarten.updated_at IS '수정 일시';

COMMENT ON COLUMN class_entity.name IS '반/학급 이름';

COMMENT ON COLUMN class_entity.grade IS '학년/연령대(예: 만3/만4/만5)';

COMMENT ON COLUMN class_entity.academic_year IS '학년도(예: 2026)';

COMMENT ON COLUMN class_entity.start_date IS '운영 시작일';

COMMENT ON COLUMN class_entity.end_date IS '운영 종료일';

COMMENT ON COLUMN class_entity.status IS '상태(운영/종료/폐지 등)';

COMMENT ON COLUMN class_entity.created_at IS '생성 일시';

COMMENT ON COLUMN class_entity.updated_at IS '수정 일시';

COMMENT ON COLUMN child.class_id IS '현재 반 ID(캐시/현재상태). 변경 이력은 CHILD_CLASS_ASSIGNMENT를 기준으로 함';

COMMENT ON COLUMN child.name IS '원아 이름';

COMMENT ON COLUMN child.child_no IS '원아 번호(원내 관리번호)';

COMMENT ON COLUMN child.rrn_encrypted IS '주민등록번호 암호문(암호화 저장)';

COMMENT ON COLUMN child.rrn_first6 IS '주민등록번호 앞 6자리(생년월일, 검색/중복확인용)';

COMMENT ON COLUMN child.birth_date IS '생년월일';

COMMENT ON COLUMN child.gender IS '성별';

COMMENT ON COLUMN child.address IS '주소';

COMMENT ON COLUMN child.enroll_date IS '입원/등록일';

COMMENT ON COLUMN child.leave_date IS '퇴원/전원/졸업일';

COMMENT ON COLUMN child.status IS '상태(재원/퇴원/휴원 등)';

COMMENT ON COLUMN child.created_at IS '생성 일시';

COMMENT ON COLUMN child.updated_at IS '수정 일시';

COMMENT ON COLUMN child_class_assignment.start_date IS '배정 시작일';

COMMENT ON COLUMN child_class_assignment.end_date IS '배정 종료일';

COMMENT ON COLUMN child_class_assignment.reason IS '변경 사유(전반/이동/신설반 등)';

COMMENT ON COLUMN child_class_assignment.note IS '비고/메모';

COMMENT ON COLUMN child_class_assignment.status IS '상태(유효/종료/취소 등)';

COMMENT ON COLUMN child_class_assignment.created_by_user_id IS '생성자 사용자 ID';

COMMENT ON COLUMN child_class_assignment.created_at IS '생성 일시';

COMMENT ON COLUMN child_class_assignment.updated_at IS '수정 일시';

COMMENT ON COLUMN teacher.staff_no IS '직원 번호(원내 사번)';

COMMENT ON COLUMN teacher.name IS '교사 이름';

COMMENT ON COLUMN teacher.gender IS '성별';

COMMENT ON COLUMN teacher.phone IS '휴대폰 번호';

COMMENT ON COLUMN teacher.email IS '이메일';

COMMENT ON COLUMN teacher.emergency_contact_name IS '비상 연락처 이름';

COMMENT ON COLUMN teacher.emergency_contact_phone IS '비상 연락처 전화번호';

COMMENT ON COLUMN teacher.rrn_encrypted IS '주민등록번호 암호문(암호화 저장)';

COMMENT ON COLUMN teacher.rrn_first6 IS '주민등록번호 앞 6자리(생년월일, 검색/중복확인용)';

COMMENT ON COLUMN teacher.level IS '직급(예: 원장/부원장/일반교사 등)';

COMMENT ON COLUMN teacher.start_date IS '근무 시작일';

COMMENT ON COLUMN teacher.end_date IS '근무 종료일';

COMMENT ON COLUMN teacher.status IS '상태(재직/휴직/퇴직 등)';

COMMENT ON COLUMN teacher.created_at IS '생성 일시';

COMMENT ON COLUMN teacher.updated_at IS '수정 일시';

COMMENT ON COLUMN class_teacher_assignment.role IS '반 내 역할(담임/부담임/보조 등)';

COMMENT ON COLUMN class_teacher_assignment.start_date IS '배정 시작일';

COMMENT ON COLUMN class_teacher_assignment.end_date IS '배정 종료일';

COMMENT ON COLUMN class_teacher_assignment.reason IS '변경 사유';

COMMENT ON COLUMN class_teacher_assignment.note IS '비고/메모';

COMMENT ON COLUMN class_teacher_assignment.status IS '상태(유효/종료/취소 등)';

COMMENT ON COLUMN class_teacher_assignment.created_by_user_id IS '생성자 사용자 ID';

COMMENT ON COLUMN class_teacher_assignment.created_at IS '생성 일시';

COMMENT ON COLUMN class_teacher_assignment.updated_at IS '수정 일시';

COMMENT ON COLUMN room.name IS '공간/교실 이름';

COMMENT ON COLUMN room.room_code IS '공간 코드(내부 식별용)';

COMMENT ON COLUMN room.location_note IS '위치 설명(층/동/출입구 등)';

COMMENT ON COLUMN room.room_type IS '공간 유형(교실/복도/현관/놀이터 등)';

COMMENT ON COLUMN room.status IS '상태(사용/미사용/폐쇄 등)';

COMMENT ON COLUMN room.created_at IS '생성 일시';

COMMENT ON COLUMN room.updated_at IS '수정 일시';

COMMENT ON COLUMN class_room_assignment.assignment_id IS '반-공간 배정 ID';

COMMENT ON COLUMN class_room_assignment.kindergarten_id IS '유치원 ID(테넌트/필터링용)';

COMMENT ON COLUMN class_room_assignment.class_id IS '반 ID';

COMMENT ON COLUMN class_room_assignment.room_id IS '공간 ID';

COMMENT ON COLUMN class_room_assignment.start_timestamptz IS '사용 시작 일시';

COMMENT ON COLUMN class_room_assignment.end_timestamptz IS '사용 종료 일시';

COMMENT ON COLUMN class_room_assignment.purpose IS '사용 목적(수업/체육/행사/기타)';

COMMENT ON COLUMN class_room_assignment.note IS '비고/메모';

COMMENT ON COLUMN class_room_assignment.status IS '상태(유효/종료/취소 등)';

COMMENT ON COLUMN class_room_assignment.created_by_user_id IS '생성자 사용자 ID';

COMMENT ON COLUMN class_room_assignment.created_at IS '생성 일시';

COMMENT ON COLUMN class_room_assignment.updated_at IS '수정 일시';

COMMENT ON COLUMN cctv_camera.camera_id IS '카메라 ID';

COMMENT ON COLUMN cctv_camera.kindergarten_id IS '유치원 ID(KINDERGARTEN.kindergarten_id)';

COMMENT ON COLUMN cctv_camera.serial_no IS '카메라 시리얼 번호/제조사 식별자';

COMMENT ON COLUMN cctv_camera.camera_name IS '카메라 이름/별칭 또는 표시 이름';

COMMENT ON COLUMN cctv_camera.model IS '모델명';

COMMENT ON COLUMN cctv_camera.created_by_user_id IS '등록한 사용자 ID(문자열로 저장된 사용자 식별자)';

COMMENT ON COLUMN cctv_camera.status IS '상태(ACTIVE/INACTIVE/RETIRED 등)';

COMMENT ON COLUMN cctv_camera.last_seen_at IS '마지막 접속/하트비트 일시';

COMMENT ON COLUMN cctv_camera.created_at IS '생성 일시';

COMMENT ON COLUMN cctv_camera.updated_at IS '수정 일시';

COMMENT ON COLUMN room_camera_assignment.start_timestamptz IS '설치/배치 시작 일시';

COMMENT ON COLUMN room_camera_assignment.end_timestamptz IS '설치/배치 종료 일시(철거/이동 시, NULL=현재 유효)';

COMMENT ON COLUMN room_camera_assignment.created_at IS '생성 일시';

COMMENT ON COLUMN room_camera_assignment.updated_at IS '수정 일시';

COMMENT ON COLUMN guardian.name IS '보호자 이름';

COMMENT ON COLUMN guardian.rrn_encrypted IS '주민등록번호 암호문(암호화 저장)';

COMMENT ON COLUMN guardian.rrn_first6 IS '주민등록번호 앞 6자리(생년월일, 검색/중복확인용)';

COMMENT ON COLUMN guardian.gender IS '성별';

COMMENT ON COLUMN guardian.phone IS '휴대폰 번호';

COMMENT ON COLUMN guardian.email IS '이메일';

COMMENT ON COLUMN guardian.address IS '주소';

COMMENT ON COLUMN guardian.status IS '상태(활성/비활성/탈퇴 등)';

COMMENT ON COLUMN guardian.created_at IS '생성 일시';

COMMENT ON COLUMN guardian.updated_at IS '수정 일시';

COMMENT ON COLUMN child_guardian_relationship.relationship IS '관계(부/모/조부모/후견인 등)';

COMMENT ON COLUMN child_guardian_relationship.is_primary IS '주 보호자 여부';

COMMENT ON COLUMN child_guardian_relationship.priority IS '우선순위(연락/권한 순서)';

COMMENT ON COLUMN child_guardian_relationship.start_date IS '관계 시작일';

COMMENT ON COLUMN child_guardian_relationship.end_date IS '관계 종료일';

COMMENT ON COLUMN child_guardian_relationship.created_at IS '생성 일시';

COMMENT ON COLUMN child_guardian_relationship.updated_at IS '수정 일시';



COMMENT ON COLUMN user_kindergarten_membership.membership_id IS '멤버십 ID';

COMMENT ON COLUMN user_kindergarten_membership.user_id IS '사용자 ID(USER_ACCOUNT.user_id)';

COMMENT ON COLUMN user_kindergarten_membership.kindergarten_id IS '유치원 ID(KINDERGARTEN.kindergarten_id)';

COMMENT ON COLUMN user_kindergarten_membership.status IS '멤버십 상태(ACTIVE/INVITED/SUSPENDED/LEFT)';

COMMENT ON COLUMN user_kindergarten_membership.joined_at IS '가입/승인 일시';

COMMENT ON COLUMN user_kindergarten_membership.left_at IS '탈퇴/종료 일시';

COMMENT ON COLUMN user_kindergarten_membership.created_at IS '생성 일시';

COMMENT ON COLUMN user_kindergarten_membership.updated_at IS '수정 일시';

COMMENT ON COLUMN user_role_assignment.role_assignment_id IS '권한 할당 ID';

COMMENT ON COLUMN user_role_assignment.user_id IS '대상 사용자 ID(USER_ACCOUNT.user_id)';

COMMENT ON COLUMN user_role_assignment.role IS '역할(GUARDIAN/TEACHER/KINDERGARTEN_ADMIN/PLATFORM_IT_ADMIN/SUPERADMIN)';

COMMENT ON COLUMN user_role_assignment.scope_type IS '권한 범위 유형(PLATFORM/KINDERGARTEN)';

COMMENT ON COLUMN user_role_assignment.scope_id IS '권한 범위 ID(NULL=PLATFORM; KINDERGARTEN일 때 kindergarten_id)';

COMMENT ON COLUMN user_role_assignment.status IS '권한 상태(ACTIVE/REVOKED)';

COMMENT ON COLUMN user_role_assignment.granted_at IS '권한 부여 일시';

COMMENT ON COLUMN user_role_assignment.granted_by_user_id IS '권한 부여자 사용자 ID(USER_ACCOUNT.user_id)';

COMMENT ON COLUMN user_role_assignment.revoked_at IS '권한 회수 일시';

COMMENT ON COLUMN camera_stream.camera_id IS '카메라 ID(CCTV_CAMERA.camera_id)';

COMMENT ON COLUMN camera_stream.stream_url IS '스트림 URL(예: RTSP/HTTP 엔드포인트)';

COMMENT ON COLUMN camera_stream.stream_user IS '스트림 인증 사용자명';

COMMENT ON COLUMN camera_stream.stream_password_encrypted IS '스트림 인증 비밀번호(암호화 저장)';

COMMENT ON COLUMN camera_stream.protocol IS '스트림 프로토콜(RTSP/HTTP)';

COMMENT ON COLUMN camera_stream.fps IS '스트림 FPS(초당 프레임 수)';

COMMENT ON COLUMN camera_stream.resolution IS '해상도(예: 1920x1080)';

COMMENT ON COLUMN camera_stream.enabled IS '스트림 사용 여부';

COMMENT ON COLUMN camera_stream.updated_at IS '수정 일시';

COMMENT ON COLUMN ai_model.model_id IS 'AI 모델 ID';

COMMENT ON COLUMN ai_model.name IS '모델 이름';

COMMENT ON COLUMN ai_model.version IS '모델 버전';

COMMENT ON COLUMN ai_model.status IS '모델 상태(예: ACTIVE/DEPRECATED 등)';

COMMENT ON COLUMN ai_model.created_at IS '생성 일시';

COMMENT ON COLUMN ai_model.updated_at IS '수정 일시';

COMMENT ON COLUMN detection_session.session_id IS '탐지 세션 ID';

COMMENT ON COLUMN detection_session.kindergarten_id IS '유치원 ID(KINDERGARTEN.kindergarten_id)';

COMMENT ON COLUMN detection_session.camera_id IS '카메라 ID(CCTV_CAMERA.camera_id)';

COMMENT ON COLUMN detection_session.model_id IS '모델 ID(AI_MODEL.model_id)';

COMMENT ON COLUMN detection_session.started_at IS '세션 시작 일시';

COMMENT ON COLUMN detection_session.ended_at IS '세션 종료 일시';

COMMENT ON COLUMN detection_session.status IS '세션 상태(예: RUNNING/ENDED/FAILED 등)';

COMMENT ON COLUMN detection_session.avg_latency_ms IS '평균 지연 시간(ms)';

COMMENT ON COLUMN detection_session.inference_fps IS '추론 처리 FPS';

COMMENT ON COLUMN detection_event.event_id IS '탐지 이벤트 ID';

COMMENT ON COLUMN detection_event.kindergarten_id IS '유치원 ID(KINDERGARTEN.kindergarten_id)';

COMMENT ON COLUMN detection_event.camera_id IS '카메라 ID(CCTV_CAMERA.camera_id)';

COMMENT ON COLUMN detection_event.room_id IS '룸/구역 ID(ROOM 테이블 FK 가정)';

COMMENT ON COLUMN detection_event.session_id IS '탐지 세션 ID(DETECTION_SESSION.session_id)';

COMMENT ON COLUMN detection_event.event_type IS '이벤트 유형(탐지 카테고리/코드)';

COMMENT ON COLUMN detection_event.severity IS '심각도(정수 등급)';

COMMENT ON COLUMN detection_event.confidence IS '신뢰도(0~1)';

COMMENT ON COLUMN detection_event.detected_at IS '탐지 발생 일시';

COMMENT ON COLUMN detection_event.start_time IS '이벤트 시작 시각';

COMMENT ON COLUMN detection_event.end_time IS '이벤트 종료 시각';

COMMENT ON COLUMN detection_event.status IS '이벤트 상태(NEW/ACKED/RESOLVED/FALSE_POSITIVE)';

COMMENT ON COLUMN detection_event.created_at IS '생성 일시';

COMMENT ON COLUMN detection_event.updated_at IS '수정 일시';

COMMENT ON COLUMN event_review.review_id IS '이벤트 리뷰/처리 ID';

COMMENT ON COLUMN event_review.event_id IS '이벤트 ID(DETECTION_EVENT.event_id)';

COMMENT ON COLUMN event_review.kindergarten_id IS '유치원 ID(KINDERGARTEN.kindergarten_id)';

COMMENT ON COLUMN event_review.user_id IS '처리 사용자 ID(USER_ACCOUNT.user_id)';

COMMENT ON COLUMN event_review.action IS '처리 액션(ACK/RESOLVE/MARK_FALSE_POSITIVE/REOPEN)';

COMMENT ON COLUMN event_review.result_status IS '처리 결과 상태(액션 후 이벤트 상태 등)';

COMMENT ON COLUMN event_review.comment IS '코멘트/메모';

COMMENT ON COLUMN event_review.created_at IS '생성 일시';

COMMENT ON COLUMN event_evidence.evidence_id IS '증거 자료 ID';

COMMENT ON COLUMN event_evidence.event_id IS '이벤트 ID(DETECTION_EVENT.event_id)';

COMMENT ON COLUMN event_evidence.kindergarten_id IS '유치원 ID(KINDERGARTEN.kindergarten_id)';

COMMENT ON COLUMN event_evidence.type IS '증거 유형(IMAGE/VIDEO)';

COMMENT ON COLUMN event_evidence.storage_uri IS '저장 위치 URI(internal MinIO/NAS 등)';

COMMENT ON COLUMN event_evidence.mime_type IS 'MIME 타입(예: image/jpeg, video/mp4)';

COMMENT ON COLUMN event_evidence.created_at IS '생성 일시';

COMMENT ON COLUMN event_evidence.retention_until IS '보관 만료 예정 일시';

COMMENT ON COLUMN event_evidence.hold IS '보관 홀드 여부(만료/삭제 방지 플래그)';

COMMENT ON COLUMN event_evidence.hash IS '무결성 확인용 해시(파일 해시)';

COMMENT ON COLUMN device_token.device_id IS '디바이스 토큰 ID';

COMMENT ON COLUMN device_token.user_id IS '사용자 ID(USER_ACCOUNT.user_id)';

COMMENT ON COLUMN device_token.platform IS '플랫폼(IOS/ANDROID)';

COMMENT ON COLUMN device_token.push_token IS '푸시 토큰(APNs/FCM 등)';

COMMENT ON COLUMN device_token.status IS '토큰 상태(예: ACTIVE/EXPIRED/REVOKED 등)';

COMMENT ON COLUMN device_token.last_seen_at IS '마지막 사용/갱신 일시';

COMMENT ON COLUMN device_token.created_at IS '생성 일시';

COMMENT ON COLUMN notification_rule.rule_id IS '알림 규칙 ID';

COMMENT ON COLUMN notification_rule.kindergarten_id IS '유치원 ID(KINDERGARTEN.kindergarten_id)';

COMMENT ON COLUMN notification_rule.user_id IS '규칙 소유/대상 사용자 ID(USER_ACCOUNT.user_id)';

COMMENT ON COLUMN notification_rule.scope_type IS '규칙 적용 범위(ROOM/CAMERA/KINDERGARTEN)';

COMMENT ON COLUMN notification_rule.scope_id IS '범위 ID(scope_type에 따른 room_id/camera_id/kindergarten_id 등)';

COMMENT ON COLUMN notification_rule.event_type IS '대상 이벤트 유형';

COMMENT ON COLUMN notification_rule.min_severity IS '최소 심각도(이 값 이상만 알림)';

COMMENT ON COLUMN notification_rule.quiet_hours_json IS '방해금지 시간 설정(JSON 문자열)';

COMMENT ON COLUMN notification_rule.enabled IS '규칙 활성화 여부';

COMMENT ON COLUMN notification_rule.created_at IS '생성 일시';

COMMENT ON COLUMN notification.notification_id IS '알림 ID';

COMMENT ON COLUMN notification.kindergarten_id IS '유치원 ID(KINDERGARTEN.kindergarten_id)';

COMMENT ON COLUMN notification.event_id IS '관련 이벤트 ID(DETECTION_EVENT.event_id)';

COMMENT ON COLUMN notification.recipient_user_id IS '수신자 사용자 ID(USER_ACCOUNT.user_id)';

COMMENT ON COLUMN notification.channel IS '전송 채널(PUSH/SMS/EMAIL)';

COMMENT ON COLUMN notification.title IS '알림 제목';

COMMENT ON COLUMN notification.body IS '알림 본문';

COMMENT ON COLUMN notification.status IS '전송 상태(QUEUED/SENT/FAILED)';

COMMENT ON COLUMN notification.sent_at IS '전송 완료/시도 일시';

COMMENT ON COLUMN notification.fail_reason IS '실패 사유';

COMMENT ON COLUMN notification.retry_count IS '재시도 횟수';

COMMENT ON COLUMN notification.created_at IS '생성 일시';

COMMENT ON COLUMN audit_log.audit_id IS '감사/추적 로그 ID';

COMMENT ON COLUMN audit_log.kindergarten_id IS '유치원 ID(KINDERGARTEN.kindergarten_id)';

COMMENT ON COLUMN audit_log.user_id IS '행위 사용자 ID(USER_ACCOUNT.user_id)';

COMMENT ON COLUMN audit_log.action IS '행위(예: CREATE/UPDATE/DELETE/LOGIN 등)';

COMMENT ON COLUMN audit_log.resource_type IS '대상 리소스 유형(테이블/도메인명 등)';

COMMENT ON COLUMN audit_log.resource_id IS '대상 리소스 ID';

COMMENT ON COLUMN audit_log.ip IS '요청 IP';

COMMENT ON COLUMN audit_log.user_agent IS 'User-Agent';

COMMENT ON COLUMN audit_log.created_at IS '생성 일시';

ALTER TABLE class_entity ADD FOREIGN KEY (kindergarten_id) REFERENCES kindergarten (kindergarten_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE room ADD FOREIGN KEY (kindergarten_id) REFERENCES kindergarten (kindergarten_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE cctv_camera ADD FOREIGN KEY (kindergarten_id) REFERENCES kindergarten (kindergarten_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE teacher ADD FOREIGN KEY (kindergarten_id) REFERENCES kindergarten (kindergarten_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE guardian ADD FOREIGN KEY (kindergarten_id) REFERENCES kindergarten (kindergarten_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE child ADD FOREIGN KEY (kindergarten_id) REFERENCES kindergarten (kindergarten_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE child ADD FOREIGN KEY (kindergarten_id, class_id) REFERENCES class_entity (kindergarten_id, class_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE user_role_assignment ADD FOREIGN KEY (user_id) REFERENCES user_account (user_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE guardian ADD FOREIGN KEY (user_id) REFERENCES user_account (user_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE teacher ADD FOREIGN KEY (user_id) REFERENCES user_account (user_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE cctv_camera ADD FOREIGN KEY (camera_id) REFERENCES camera_stream (camera_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE detection_session ADD FOREIGN KEY (model_id) REFERENCES ai_model (model_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE event_review ADD FOREIGN KEY (user_id) REFERENCES user_account (user_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE event_review ADD FOREIGN KEY (kindergarten_id, event_id) REFERENCES detection_event (kindergarten_id, event_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE event_evidence ADD FOREIGN KEY (kindergarten_id, event_id) REFERENCES detection_event (kindergarten_id, event_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE notification ADD FOREIGN KEY (kindergarten_id, event_id) REFERENCES detection_event (kindergarten_id, event_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE device_token ADD FOREIGN KEY (user_id) REFERENCES user_account (user_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE notification_rule ADD FOREIGN KEY (kindergarten_id) REFERENCES kindergarten (kindergarten_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE notification_rule ADD FOREIGN KEY (user_id) REFERENCES user_account (user_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE notification ADD FOREIGN KEY (recipient_user_id) REFERENCES user_account (user_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE audit_log ADD FOREIGN KEY (kindergarten_id) REFERENCES kindergarten (kindergarten_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE audit_log ADD FOREIGN KEY (user_id) REFERENCES user_account (user_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE class_teacher_assignment ADD FOREIGN KEY (kindergarten_id, class_id) REFERENCES class_entity (kindergarten_id, class_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE class_teacher_assignment ADD FOREIGN KEY (kindergarten_id, teacher_id) REFERENCES teacher (kindergarten_id, teacher_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE child_guardian_relationship ADD FOREIGN KEY (kindergarten_id, child_id) REFERENCES child (kindergarten_id, child_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE child_guardian_relationship ADD FOREIGN KEY (kindergarten_id, guardian_id) REFERENCES guardian (kindergarten_id, guardian_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE user_kindergarten_membership ADD FOREIGN KEY (user_id) REFERENCES user_account (user_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE user_kindergarten_membership ADD FOREIGN KEY (kindergarten_id) REFERENCES kindergarten (kindergarten_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE class_room_assignment ADD FOREIGN KEY (kindergarten_id, class_id) REFERENCES class_entity (kindergarten_id, class_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE class_room_assignment ADD FOREIGN KEY (kindergarten_id, room_id) REFERENCES room (kindergarten_id, room_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE child_class_assignment ADD FOREIGN KEY (kindergarten_id, child_id) REFERENCES child (kindergarten_id, child_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE child_class_assignment ADD FOREIGN KEY (kindergarten_id, class_id) REFERENCES class_entity (kindergarten_id, class_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE room_camera_assignment ADD FOREIGN KEY (kindergarten_id, room_id) REFERENCES room (kindergarten_id, room_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE room_camera_assignment ADD FOREIGN KEY (kindergarten_id, camera_id) REFERENCES cctv_camera (kindergarten_id, camera_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE detection_session ADD FOREIGN KEY (kindergarten_id) REFERENCES kindergarten (kindergarten_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE detection_session ADD FOREIGN KEY (kindergarten_id, camera_id) REFERENCES cctv_camera (kindergarten_id, camera_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE detection_event ADD FOREIGN KEY (kindergarten_id, session_id) REFERENCES detection_session (kindergarten_id, session_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE detection_event ADD FOREIGN KEY (kindergarten_id, camera_id) REFERENCES cctv_camera (kindergarten_id, camera_id) DEFERRABLE INITIALLY IMMEDIATE;

ALTER TABLE detection_event ADD FOREIGN KEY (kindergarten_id, room_id) REFERENCES room (kindergarten_id, room_id) DEFERRABLE INITIALLY IMMEDIATE;
