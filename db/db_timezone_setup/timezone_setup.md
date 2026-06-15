# DB Timezone Setup (KST)

## 목적
- 이 프로젝트의 DB 타임존을 한국 시간(`Asia/Seoul`, KST)으로 고정한다.
- Docker 환경/DB 세션/애플리케이션 조회 시점의 시간 기준을 일관되게 유지한다.

## 적용 위치
- `db/postgresSQL-docker-compose.yml`
  - `TZ=Asia/Seoul`
  - `PGTZ=Asia/Seoul`
- `db/initdb/00_init.sql`
  - `SET TIME ZONE 'Asia/Seoul';`
  - `ALTER DATABASE ... SET timezone TO 'Asia/Seoul';`

## 권장 설정

### 1) Docker Compose 환경변수
PostgreSQL 서비스에 아래 환경변수를 설정한다.

```yaml
environment:
  POSTGRES_DB: ${POSTGRES_DB}
  POSTGRES_USER: ${POSTGRES_USER}
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
  TZ: Asia/Seoul
  PGTZ: Asia/Seoul
```

### 2) DB 초기화 SQL
`db/initdb/00_init.sql` 상단에 아래 구문을 추가한다.

```sql
SET TIME ZONE 'Asia/Seoul';

DO $$
DECLARE
    db_name text := current_database();
BEGIN
    EXECUTE format('ALTER DATABASE %I SET timezone TO %L', db_name, 'Asia/Seoul');
END $$;
```

## 이미 생성된 DB에 적용하는 방법
`docker-entrypoint-initdb.d`는 **최초 볼륨 생성 시 1회**만 실행된다.  
기존 볼륨을 사용 중이라면 아래 명령으로 즉시 반영한다.

```bash
docker exec ai-kids-postgres psql -U kids_user -d kids_postgres_db -c "ALTER DATABASE kids_postgres_db SET timezone TO 'Asia/Seoul';"
```

필요하면 컨테이너를 재기동한다.

```bash
docker compose up -d --build db
```

## 확인 방법

### 1) PostgreSQL 타임존 확인
```bash
docker exec ai-kids-postgres psql -U kids_user -d kids_postgres_db -c "SHOW TIMEZONE; SELECT NOW();"
```

기대 결과:
- `SHOW TIMEZONE` = `Asia/Seoul`
- `NOW()` 결과의 오프셋 = `+09`

### 2) 컨테이너 시스템 시간 확인
```bash
docker exec ai-kids-postgres date
```

기대 결과:
- `KST` 표기 또는 한국 시간대 기준 시간 출력

## 주의사항
- 운영/개발 환경 모두 동일한 타임존 정책(KST)을 유지해야 로그/데이터 비교가 쉽다.
- 애플리케이션/프론트/DB 중 하나라도 UTC로 남아 있으면 시간 차이로 보일 수 있다.
- 새 프로젝트를 세팅할 때는 `00_init.sql` 반영 여부를 먼저 확인한다.
