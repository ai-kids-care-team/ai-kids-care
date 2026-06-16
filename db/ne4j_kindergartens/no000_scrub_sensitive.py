from neo4j_connect import driver

# ============================================================
# SPEC-0001 §365 민감 속성 제거 스크립트
# ------------------------------------------------------------
# 목적:
#   기존 Neo4j 데모 그래프에 이미 적재된 S0/PII 속성을
#   REMOVE하여 그래프를 정결 상태로 만든다.
#   MERGE+SET 로더는 존재하는 속성을 덮어쓰지만 삭제하지 않으므로
#   로더 재실행 전에 이 스크립트를 먼저 실행해야 한다.
#
# 실행 예:
#   python no000_scrub_sensitive.py
#
# 멱등성:
#   이미 없는 속성을 REMOVE해도 오류 없이 통과된다.
# ============================================================


def scrub_user(tx):
    tx.run("""
        MATCH (u:User)
        REMOVE u.password_hash, u.email, u.phone
    """)


def scrub_kindergarten(tx):
    tx.run("""
        MATCH (k:Kindergarten)
        REMOVE k.address, k.contact_phone, k.contact_email
    """)


def scrub_teacher(tx):
    tx.run("""
        MATCH (t:Teacher)
        REMOVE t.rrn_encrypted, t.rrn_first6,
               t.emergency_contact_phone, t.emergency_contact_name
    """)


def scrub_child(tx):
    tx.run("""
        MATCH (c:Child)
        REMOVE c.rrn_first6, c.rrn_encrypted, c.birth_date, c.address
    """)


def scrub_guardian(tx):
    tx.run("""
        MATCH (g:Guardian)
        REMOVE g.rrn_encrypted, g.rrn_first6, g.address
    """)


if __name__ == "__main__":
    with driver.session() as session:
        session.execute_write(scrub_user)
        print("User: password_hash, email, phone 제거 완료")

        session.execute_write(scrub_kindergarten)
        print("Kindergarten: address, contact_phone, contact_email 제거 완료")

        session.execute_write(scrub_teacher)
        print("Teacher: rrn_encrypted, rrn_first6, emergency_contact_phone, emergency_contact_name 제거 완료")

        session.execute_write(scrub_child)
        print("Child: rrn_first6, rrn_encrypted, birth_date, address 제거 완료")

        session.execute_write(scrub_guardian)
        print("Guardian: rrn_encrypted, rrn_first6, address 제거 완료")

    print("SPEC-0001 §365 민감 속성 제거 완료")
