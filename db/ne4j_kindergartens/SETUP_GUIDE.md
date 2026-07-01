# Neo4j data-loader 셋업 가이드 (PG → 派生 그래프)

## 🎯 목적
Docker Compose 시작 시 `data-loader` 컨테이너가 **PostgreSQL 을 직접 조회**하여 Neo4j 派生
그래프를 **증분 sync**(장수명 상주 프로세스)로 지속 수렴시킨다. 최초 기동 시 그래프가 비었거나
워터마크가 없으면 一次性 전체 재구축(bootstrap)을 하고, 이후에는 tick 마다 워터마크 증분
upsert + 주기 대조(reconcile)로 PG 쓰기를 폴링 간격 내에 반영한다. (CSV 스냅샷은 사용하지 않는다.)

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
2. Neo4j (healthy 대기)
3. **data-loader 실행** (bootstrap 재구축 → 증분 폴링 루프, **상주**·종료하지 않음)
4. Backend / Frontend 시작

### 2. 로그 / 상태
```bash
docker compose logs -f data-loader   # tick / reconcile 로그 (프로세스는 종료되지 않음)
docker compose ps                     # data-loader 가 "running" 상태 유지
```

### 3. 폴링 파라미터 (compose env)
| env | 기본값 | 의미 |
|-----|--------|------|
| `POLL_INTERVAL_SEC` (`GRAPH_SYNC_POLL_INTERVAL_SEC`) | `30` | 증분 tick 간 sleep 초 = 정상 상태 지연 상한 |
| `RECONCILE_EVERY` (`GRAPH_SYNC_RECONCILE_EVERY`) | `1` | 몇 tick 마다 전체 id 대조로 고아 정리 |

PG 데이터 변경은 다음 tick 에 자동 반영되므로 **수동 재실행이 필요 없다**.

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
2. **상주 프로세스**: data-loader 는 `restart: unless-stopped` 로 계속 실행된다. bootstrap 후
   증분 폴링 루프에 진입하며 종료하지 않는다(backend 는 종료를 기다리지 않고 병행 기동).
3. **증분 수렴 + 대조**: 정상 상태는 `DETACH DELETE` 전체 삭제 없이 워터마크 증분 upsert →
   온라인 그래프 읽기가 빈 그래프를 보지 않는다. 하드 삭제는 주기 id 대조로 고아를 정리한다.
   최초/빈 그래프에서만 bootstrap 전체 재구축.
4. **INC-003**: 그래프 노드(및 `_GraphSyncState` 워터마크 노드)에 S0/PII 속성 금지
   (화이트리스트 SELECT — 전체·증분 두 경로 공용 + no000 방어층 + 백엔드
   `LoaderPiiProjectionGuardTest` 가드).

## 📊 데이터 흐름
```
PostgreSQL (system-of-record)
   ↓  load_graph.py (비-PII 화이트리스트 SELECT)
Neo4j Graph DB (읽기전용 파생본)
   ↓
Spring Boot Backend (GraphRepository)
```
