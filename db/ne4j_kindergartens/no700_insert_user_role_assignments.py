from neo4j_connect import driver
import csv
from datetime import datetime


# ============================================================
# user_role_assignments 적재 스크립트
# ------------------------------------------------------------
# 목적:
#   70_user_role_assignments.csv 파일을 읽어서
#   User - HAS_ROLE -> Role 관계를 생성
#
# 실행 예:
#   python load_user_role_assignments.py
#
# CSV 예:
#   role_assignment_id,user_id,role,scope_type,scope_id,status,
#   granted_at,granted_by_user_id,revoked_at,revoked_by_user_id
# ============================================================


# ============================================================
# 날짜/시간 문자열 정리 함수
# ============================================================
def fix_datetime(dt):
    if not dt or dt == "":
        return None

    value = str(dt).strip()

    if value.endswith("T"):
        value = value[:-1]

    try:
        value = value.replace(" ", "T", 1)
        return datetime.fromisoformat(value).isoformat()
    except Exception:
        return value


# ============================================================
# 문자열 정리 함수
# ============================================================
def clean_value(value):
    if value is None:
        return None

    value = str(value).strip()
    if value == "":
        return None

    return value


# ============================================================
# Role 제약조건 생성
# ------------------------------------------------------------
# role + scope_type + scope_id 조합으로 Role 노드 구분
# scope_id가 NULL일 수 있어서 문자열 키(role_key)를 사용
# ============================================================
def create_role_constraint(tx):
    tx.run("""
        CREATE CONSTRAINT role_role_key_unique IF NOT EXISTS
        FOR (r:Role)
        REQUIRE r.role_key IS UNIQUE
    """)


# ============================================================
# User-Role 관계 생성
# ------------------------------------------------------------
# 전제:
#   User 노드는 이미 적재되어 있어야 함
#
# 동작:
#   1. Role 노드 생성
#   2. User -> Role 관계 생성
# ============================================================
def create_user_role_relationship(tx, row):
    print("DEBUG ROLE ROW:", row)

    user_id = int(row["user_id"])
    role = clean_value(row.get("role"))
    scope_type = clean_value(row.get("scope_type"))
    scope_id = clean_value(row.get("scope_id"))
    status = clean_value(row.get("status"))

    # --------------------------------------------------------
    # Role 노드 유니크 키
    # 예:
    #   SUPERADMIN|PLATFORM|NULL
    # --------------------------------------------------------
    role_key = f"{role}|{scope_type}|{scope_id if scope_id is not None else 'NULL'}"

    tx.run("""
        // ======================================================
        // 1. User 조회
        // ======================================================
        MATCH (u:User {user_id: $user_id})

        // ======================================================
        // 2. Role 노드 생성/조회
        // ======================================================
        MERGE (r:Role {role_key: $role_key})
        SET r.role = $role,
            r.scope_type = $scope_type,
            r.scope_id = $scope_id

        // ======================================================
        // 3. User -> Role 관계 생성
        // ------------------------------------------------------
        // 관계 자체에 할당 이력 속성 저장
        // ======================================================
        MERGE (u)-[hr:HAS_ROLE]->(r)
        SET hr.role_assignment_id = $role_assignment_id,
            hr.status = $status,
            hr.granted_at = $granted_at,
            hr.granted_by_user_id = $granted_by_user_id,
            hr.revoked_at = $revoked_at,
            hr.revoked_by_user_id = $revoked_by_user_id
    """,
    user_id=user_id,
    role_key=role_key,
    role=role,
    scope_type=scope_type,
    scope_id=scope_id,
    role_assignment_id=int(row["role_assignment_id"]) if clean_value(row.get("role_assignment_id")) else None,
    status=status,
    granted_at=fix_datetime(row.get("granted_at")),
    granted_by_user_id=int(row["granted_by_user_id"]) if clean_value(row.get("granted_by_user_id")) else None,
    revoked_at=fix_datetime(row.get("revoked_at")),
    revoked_by_user_id=int(row["revoked_by_user_id"]) if clean_value(row.get("revoked_by_user_id")) else None
    )


# ============================================================
# 메인 실행
# ============================================================
if __name__ == "__main__":
    csv_file_path = "./data/700_user_role_assignments.csv"

    with driver.session() as session:
        # --------------------------------------------------------
        # 0. Role 제약조건 생성
        # --------------------------------------------------------
        session.execute_write(create_role_constraint)
        print("✅ constraint 생성/확인 완료: Role.role_key UNIQUE")

        # --------------------------------------------------------
        # 1. CSV 파일 읽기
        # --------------------------------------------------------
        with open(csv_file_path, "r", encoding="utf-8-sig") as file:
            reader = csv.DictReader(file)

            for row in reader:
                try:
                    if not row.get("role_assignment_id"):
                        print("⚠️ SKIP: role_assignment_id 없음", row)
                        continue

                    if not row.get("user_id"):
                        print("⚠️ SKIP: user_id 없음", row)
                        continue

                    if not row.get("role"):
                        print("⚠️ SKIP: role 없음", row)
                        continue

                    session.execute_write(create_user_role_relationship, row)

                    print(
                        f"✅ User-Role 적재 완료: "
                        f"role_assignment_id={row['role_assignment_id']}, "
                        f"user_id={row['user_id']}, "
                        f"role={row.get('role')}"
                    )

                except Exception as e:
                    print(
                        f"❌ User-Role 적재 실패: "
                        f"role_assignment_id={row.get('role_assignment_id')}, error={e}"
                    )

    print("🎉 70_user_role_assignments.csv → User-HAS_ROLE-Role 적재 완료")


# ============================================================
# 의미 있는 조회용 Cypher 쿼리 모음
# ============================================================

"""
============================================================
1. 전체 User-Role 관계 조회
============================================================
MATCH (u:User)-[hr:HAS_ROLE]->(r:Role)
RETURN u, hr, r
LIMIT 25;

설명:
- 적재가 실제로 되었는지 빠르게 확인


============================================================
2. 전체 Role 노드 조회
============================================================
MATCH (r:Role)
RETURN r;

설명:
- 어떤 role 종류가 생성되었는지 확인


============================================================
3. 특정 사용자 권한 조회
============================================================
MATCH (u:User {user_id: 1})-[hr:HAS_ROLE]->(r:Role)
RETURN u.user_id, u.login_id, r.role, r.scope_type, r.scope_id, hr.status;

설명:
- 한 명의 권한 확인


============================================================
4. role별 사용자 수
============================================================
MATCH (u:User)-[:HAS_ROLE]->(r:Role)
RETURN r.role AS role, count(u) AS user_count
ORDER BY user_count DESC;

설명:
- SUPERADMIN, TEACHER, GUARDIAN 등 분포 확인


============================================================
5. ACTIVE 권한만 조회
============================================================
MATCH (u:User)-[hr:HAS_ROLE]->(r:Role)
WHERE hr.status = 'ACTIVE'
RETURN u.user_id, r.role, r.scope_type, r.scope_id, hr.granted_at
ORDER BY hr.granted_at DESC;

설명:
- 현재 유효한 권한만 보기


============================================================
6. PLATFORM 범위 권한 조회
============================================================
MATCH (u:User)-[hr:HAS_ROLE]->(r:Role)
WHERE r.scope_type = 'PLATFORM'
RETURN u.user_id, r.role, r.scope_type, r.scope_id, hr.status;

설명:
- 플랫폼 전체 권한 확인


============================================================
7. Role 노드 전체 삭제 테스트용
============================================================
MATCH (r:Role)
DETACH DELETE r;

설명:
- 테스트 환경에서만 사용
- HAS_ROLE 관계도 같이 삭제됨


============================================================
8. HAS_ROLE 관계만 삭제 테스트용
============================================================
MATCH ()-[r:HAS_ROLE]->()
DELETE r;

설명:
- User, Role 노드는 유지
- 관계만 재생성하고 싶을 때 사용
"""