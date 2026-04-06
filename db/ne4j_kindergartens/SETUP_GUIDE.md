# Kids 프로젝트 Neo4j 데이터 적재 자동화 가이드

## 🎯 목적
Spring Boot 백엔드가 Docker로 시작될 때, Python 스크립트를 자동으로 실행하여 Neo4j 그래프 DB에 데이터를 적재하는 자동화 시스템

## 📁 프로젝트 구조

```
projects/kids/
├── ne4j_kindergartens/          # Python Neo4j 데이터 적재 스크립트
│   ├── Dockerfile               # [생성됨] Python 컨테이너 빌드
│   ├── run_all.sh               # [생성됨] 모든 스크립트 실행
│   ├── neo4j_connect.py         # [수정됨] Docker 네트워크 연결 주소 변경
│   ├── requirements.txt         # neo4j 라이브러리
│   ├── no100_insert_users.py    # User 노드 생성
│   ├── no200_insert_kindergarter.py
│   ├── no300_insert_teachers.py
│   ├── no400_insert_classes.py
│   ├── no500_insert_children.py
│   ├── no600_insert_guardians.py
│   ├── no700_insert_user_role_assignments.py
│   ├── no800_child_guadian_relationships.py
│   ├── no900_class_teacher_assignments.py
│   ├── no950_child_class_assignments.py
│   ├── no1000_create_relationships.py
│   └── data/                    # CSV 데이터 파일들
│
└── last-DEV-ai-kids-care/       # Spring Boot 풀스택 프로젝트
    ├── docker-compose.yml       # [수정됨] 전체 서비스 정의
    ├── backend/
    │   └── src/main/resources/application.yml  # [수정됨] Neo4j 설정
    ├── frontend/
    └── db/
```

## 🚀 실행 방법

### 1. 전체 서비스 시작 (자동 데이터 적재)

```bash
cd /Users/smkim-macmini/projects/kids/last-DEV-ai-kids-care
docker compose up -d
```

**자동 실행 순서:**
1. PostgreSQL 시작
2. Neo4j 시작
3. **Python Data Loader 실행** (CSV → Neo4j 데이터 적재)
4. Spring Boot Backend 시작 (데이터 적재 완료 후)
5. Frontend 시작

### 2. 로그 확인

```bash
# Python 스크립트 실행 로그
docker compose logs -f data-loader

# Backend 로그
docker compose logs -f backend

# 전체 로그
docker compose logs -f
```

### 3. 데이터 적재 완료 확인

```bash
# data-loader 상태가 "exited"면 완료
docker compose ps

# Neo4j 접속 확인
curl http://localhost:7474
```

## 🔍 문제 해결

### 데이터 적재 재실행

```bash
# 기존 컨테이너 제거 후 재실행
docker compose down data-loader
docker compose up -d data-loader
```

### 수동 테스트

```bash
# 개별 스크립트 테스트
docker compose run --rm data-loader python no100_insert_users.py

# 전체 스크립트 재실행
docker compose run --rm data-loader
```

### Neo4j 데이터 확인

```bash
# Neo4j Browser 접속
http://localhost:7474

# Cypher 쿼리 실행
MATCH (u:User) RETURN count(u) AS user_count;
MATCH (k:Kindergarten) RETURN count(k) AS kindergarten_count;
```

## 📝 수정 사항

### 1. neo4j_connect.py
- 연결 주소: `localhost:7687` → `neo4j:7687` (Docker 네트워크)

### 2. docker-compose.yml
- Neo4j 서비스 추가
- Python data-loader 서비스 추가
- Backend 의존성 설정 (data-loader 완료 후 시작)

### 3. application.yml (백엔드)
- Neo4j 연결 설정 추가

## ⚠️ 주의사항

1. **CSV 파일 위치**: `ne4j_kindergartens/data/` 폴더에 CSV 파일이 반드시 있어야 함
2. **실행 순서**: data-loader가 완료되어야 backend가 시작됨
3. **네트워크**: Docker 내부에서는 서비스명(`neo4j`, `db`)으로 연결
4. **권한**: `run_all.sh`에 실행 권한 필요 (`chmod +x`)

## 📊 데이터 적재 흐름

```
CSV 파일
   ↓
Python 스크립트 (Docker 컨테이너)
   ↓
Neo4j Graph DB
   ↓
Spring Boot Backend (사용 가능)
```

## 🔗 참고 링크

- Neo4j Docker: https://hub.docker.com/_/neo4j
- Spring Data Neo4j: https://spring.io/projects/spring-data-neo4j
- Docker Compose: https://docs.docker.com/compose/
