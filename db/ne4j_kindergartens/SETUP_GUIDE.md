# Neo4j data-loader 셋업 가이드 (PG → 派生 그래프)

## 🎯 목적
Docker Compose 시작 시 `data-loader` 컨테이너가 **PostgreSQL 을 직접 조회**하여 Neo4j 派生
그래프를 일회성으로 재구축한다. (CSV 스냅샷은 사용하지 않는다.)

## 📁 디렉토리 구조

```
db/ne4j_kindergartens/
├── Dockerfile                  # python:3.11-slim + neo4j / psycopg2
├── run_all.sh                  # 入口: no000 → load_graph
├── config.py                   # 환경변수 기반 PG/Neo4j 설정 (단일 출처)
├── neo4j_connect.py            # 공유 Neo4j 드라이버 (no000 용)
├── no000_scrub_sensitive.py    # [INC-003 방어층] 잔존 S0/PII 속성 REMOVE (먼저 실행)
├── load_graph.py               # [핵심] PG → Neo4j ETL (비-PII 화이트리스트)
└── requirements.txt
```

## 🚀 실행

### 1. 전체 서비스 시작 (자동 적재)
```bash
docker compose up -d --build
```
자동 실행 순서:
1. PostgreSQL (healthy 대기)
2. Neo4j (started 대기)
3. **data-loader 실행** (PostgreSQL → Neo4j 재구축, 완료 후 종료)
4. Backend / Frontend 시작

### 2. 로그 / 상태
```bash
docker compose logs -f data-loader   # 적재 로그
docker compose ps                     # data-loader 가 "exited (0)" 이면 성공
```

### 3. 재실행 (PG 데이터 변경 후 재동기화)
```bash
docker compose run --rm data-loader
```

### 4. Neo4j 데이터 확인
```bash
# http://localhost:7474  또는 cypher-shell
MATCH (n) RETURN labels(n)[0] AS label, count(*) ORDER BY label;
MATCH (k:Kindergarten)-[:HAS_TEACHER]->(t:Teacher)-[:HAS_CLASS]->(c:Class)
      -[:HAS_CHILD]->(ch:Child)-[:HAS_GUARDIAN]->(g:Guardian)
RETURN count(*) AS full_paths;
```

## ⚠️ 주의사항
1. **데이터 소스 = PostgreSQL**: loader 는 `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`
   환경변수로 PG 에 접속한다. CSV 파일은 없다.
2. **실행 순서**: data-loader 가 정상 종료(코드 0)되어야 backend 가 시작된다. PG 접속 실패/쿼리
   오류 시 loader 는 비-0 코드로 종료하여 빈 그래프를 만들지 않는다.
3. **클린 재구축**: 매 실행 시 `MATCH (n) DETACH DELETE n` 으로 전체 삭제 후 재적재 → 그래프가
   현재 PG 상태를 정확히 반영.
4. **INC-003**: 그래프 노드에 S0/PII 속성 금지 (화이트리스트 SELECT + no000 방어층 + 백엔드
   `LoaderPiiProjectionGuardTest` 가드).

## 📊 데이터 흐름
```
PostgreSQL (system-of-record)
   ↓  load_graph.py (비-PII 화이트리스트 SELECT)
Neo4j Graph DB (읽기전용 파생본)
   ↓
Spring Boot Backend (GraphRepository)
```
