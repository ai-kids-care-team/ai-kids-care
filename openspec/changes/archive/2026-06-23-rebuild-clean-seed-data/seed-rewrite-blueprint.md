# Seed 重写蓝图(精简自洽小集 + 一条演示链)

> Task 2 执行指南。按本蓝图重写 `db/initdb/` 业务 seed。**不改** `24_kindergartens`(3 园原样)、`40_ai_models`(6 行原样)。`23_device_tokens` 已删。
> 红线见 `seed-test-contract.md`。原样保留:`admin`(user_id=1 整行)、`children.child_id=1` 整行(rrn_hash=`0Vtg2m20v_7y5MW6tlmYIl2e51mKmM4C8OwmCTUz0x0`)。
> FK 拓扑序 = 文件编号。所有 created_at/updated_at 用具体过去时间或 now()。password_hash 统一用 admin 的有效 bcrypt `$2a$10$gsdHDVEwKTLkDq0j.mIuY.ole1xRlLqALItBj7IRxOg62jWNHMXFK`。

## id 方案(沿用原段位约定的紧凑版:1-10 超管 / 园1 教师101+ 家长121+ / 园2 401+/421+ / 园3 701+/721+)

### 21_users(10 行) — phone 用 `010-1000-XXXX`(避开 contract C),login_id 见下(避开 C 的测试账号)
| user_id | login_id | role 用途 | 园 | phone |
|--|--|--|--|--|
| 1 | admin | superadmin | - | (原样整行,勿改) |
| 101 | director-kg1 | KGADMIN+teacher(DIRECTOR) | 1 | 010-1000-0101 |
| 102 | teacher-kg1 | TEACHER | 1 | 010-1000-0102 |
| 121 | guardian-kg1 | GUARDIAN | 1 | 010-1000-0121 |
| 401 | director-kg2 | KGADMIN+teacher | 2 | 010-1000-0401 |
| 402 | teacher-kg2 | TEACHER | 2 | 010-1000-0402 |
| 421 | guardian-kg2 | GUARDIAN | 2 | 010-1000-0421 |
| 701 | director-kg3 | KGADMIN+teacher | 3 | 010-1000-0701 |
| 702 | teacher-kg3 | TEACHER | 3 | 010-1000-0702 |
| 721 | guardian-kg3 | GUARDIAN | 3 | 010-1000-0721 |

### 22_superadmins(1): superadmin_id=1, user_id=1, name '최서준', ACTIVE.
### 25_classes(6): 1,2→园1(ACTIVE); 3,4→园2; 5,6→园3. grade '만5세'/'만4세', academic_year 2025, start 2025-03-01 end 2026-02-28.
### 26_children(6): **child_id=1 原样整行**(园1). 2→园1; 3,4→园2; 5,6→园3. 新行 rrn_first6 任意6位, rrn_hash `FIXTURE-HASH-c<N>`, status ACTIVE, gender/birth 合理.
### 27_rooms(9) — room_type 与 name 自洽: 园1 1(교실1,교실)2(교실2,교실)3(놀이터,놀이터); 园2 4,5,6(교실1,교실2,놀이터); 园3 7,8,9(同). status ACTIVE.
### 28_cctv_cameras(3): camera 1→园1(created_by 101), 2→园2(401), 3→园3(701). ACTIVE. serial 'CAM-0001'..
### 29_guardians(3): guardian_id 1(园1,user121),2(园2,user421),3(园3,user721). rrn_hash FIXTURE-HASH-g<N>, rrn_first6 6位, status ACTIVE.
### 30_teachers(6): teacher_id 1(园1,user101,level DIRECTOR),2(园1,user102,TEACHER... level 用 '일반' 或现有合法值, 见下注),3(园2,user401,DIRECTOR),4(园2,user402),5(园3,user701,DIRECTOR),6(园3,user702). rrn_hash FIXTURE-HASH-t<N>, start_date 2024-03-01, status ACTIVE. **level enum 待确认**: 现有用 'DIRECTOR'; 非园长用值需确认(可全用 'DIRECTOR' 规避,或读 schema 确认 teacher level enum)。
### 31_user_kindergarten_memberships(9): 每非 admin user → 其园 ACTIVE membership.
### 32_user_role_assignments(10): user1 SUPERADMIN/PLATFORM/scope null; 101 KINDERGARTEN_ADMIN/KINDERGARTEN/scope 1; 102 TEACHER/KINDERGARTEN/1; 121 GUARDIAN/KINDERGARTEN/1; 401/402/421→园2; 701/702/721→园3. status ACTIVE.
### 33_notification_rules(3): rule 1(user101,kg1,KINDERGARTEN,target_id 1,event 'FIGHTING',min_severity 1,quiet_hours_json `{"start":"22:00","end":"07:00"}`,enabled true); 2(user401,kg2); 3(user701,kg3).
### 34_child_class_assignments(6): child1→class1, child2→class2(园1); child3→class3,child4→class4(园2); child5→class5,child6→class6(园3). status ACTIVE, start 2025-03-01.
### 35_class_teacher_assignments(6): teacher1→class1(HOMEROOM,ACTIVE), t2→c2, t3→c3, t4→c4, t5→c5, t6→c6. start 2025-03-01.
### 36_class_room_assignments(6): class1→room1, c2→room2(园1); c3→room4,c4→room5(园2); c5→room7,c6→room8(园3). status ACTIVE, start_at 2025-03-01, end_at null(含 now). purpose null.
### 37_child_guardian_relationships(6): g1→child1(FATHER,is_primary true,priority 1), g1→child2(priority 2); g2→child3,child4; g3→child5,child6. start 2025-03-01, end null.
### 38_room_camera_assignments(3): camera1→room1(园1,end_at NULL active), camera2→room4(园2), camera3→room7(园3). start_at 2025-03-01.
### 39_camera_streams(3): `DELETE FROM "camera_streams";` + stream 1(kg1,camera1,MAIN,active,is_primary true,enabled,ACTIVE,凭据列全 NULL), 2(kg2,camera2), 3(kg3,camera3). 20 列见原文件.
### 40_ai_models: **不改**(6 行,model_id=3 在).
### 41_detection_sessions(1): session1(kg1,camera1,stream1,model_id 3,ACTIVE,started/ended/latency/fps).
### 42_detection_events(2): event1(kg1,camera1,room1=교실,session1,event_type 'OTHER',severity 5,confidence 0.94,detected_at,RESOLVED ← 演示链,EventReview 会 reset OPEN); event2(kg1,camera1,room3=놀이터 公共空间样本,session1,'OTHER',severity 3,OPEN). EventReview 取 ORDER BY event_id LIMIT 1 = event1.
### 43_event_reviews(1): review1(event1,kg1,user101,from 'ACKNOWLEDGED',result 'DISMISSED',comment).
### 44_event_evidence_files(1): evidence1(event1,kg1,IMAGE,uri,hash,mime image/jpeg).
### 45_notifications(1): notif1(event1,kg1,recipient_user_id 121,channel 'SMS',title,body,status 'READ',dedupe_key 唯一,retry_count 0).
### 46_appreciation_letters(1): letter1(kg1,sender_user_id 121=guardian,target_type 'TEACHER',target_id 1=teacher_id,title,content `$$...$$`,is_public true,status 'ACTIVE').
### 88_announcements(2): 简单 INSERT…VALUES(author_id 1,title,body,status 'ACTIVE',starts_at,published_at,view_count 0,is_pinned false). 弃用原 INSERT…SELECT 复杂式.

## 验证
灌库(ContextLoadSmokeTest)验 FK → 全套件验锚点 → SchemaConsistencyGuard. 迭代修 enum/约束错(teacher.level、各 status_enum、event_type_enum、channel)。
