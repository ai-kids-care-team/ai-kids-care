도커에 컨테이너로 db redis 를 이용해서 user 테이블은 아이디를 키로 하고 value는 패스워드로 생성할꺼야


docker compose up -d
docker ps


정상실행되면
local-postgres
Up
5432->5432

접속테스트

docker exec -it local-postgres psql -U child_user -d child_db


데이터그립용
host=localhost
port=5432
database=child_db
user=child_user
password=child_pass



cd db
docker compose --env-file .\.env -f .\postgresSQL-docker-compose.yml down -v
docker compose --env-file .\.env -f .\postgresSQL-docker-compose.yml up -d


* redis 도 같이 삭제
docker compose -f .\postgresSQL-docker-compose.yml down -v --remove-orphans
docker rm -f ai-child-redis

docker compose -f .\postgresSQL-docker-compose.yml down -v


*적용되었는지 검증
docker exec -it local-postgres env | findstr POSTGRES

*정상출력
POSTGRES_USER=child_user
POSTGRES_PASSWORD=child_pass
POSTGRES_DB=child_db


## Guardian 샘플 데이터

문서:
- [guardian_RefData.md](./db_sample/guardian_RefData.md)

실행 SQL:
- [05_guardian_seed.sql](./initdb/05_guardian_seed.sql)

실행 명령(프로젝트 현재 컨테이너 기준):
```bash
docker exec -i ai-kids-postgres psql -U kids_user -d kids_postgres_db < db/initdb/05_guardian_seed.sql
```


## 한글 안전하게 넣는 방법

권장 순서:
1) **UTF-8 인코딩된 `.sql` 파일**로 작성
2) PowerShell에서 **코드페이지 UTF-8**로 전환
3) `psql -f` 또는 리다이렉션(`<`)으로 파일 실행

PowerShell 예시:
```powershell
chcp 65001
$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
docker exec -i ai-kids-postgres psql -U kids_user -d kids_postgres_db < db/initdb/05_guardian_seed.sql
```

추가 권장:
- 인라인 `-c "한글 SQL..."` 방식보다 **파일 실행 방식**을 우선 사용
- 필요 시 클라이언트 인코딩 명시:
  - `PGCLIENTENCODING=UTF8`

## DB 타임존 설정

문서:
- [timezone_setup.md](./db_timezone_setup/timezone_setup.md)
