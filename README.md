# AI Kids Care

React + Spring Boot 기반 풀스택 대시보드 MVP입니다. 모든 서비스는 Docker 컨테이너로 구동됩니다.

## 기술 스택

- **Frontend**: React, Next.js, TypeScript, Tailwind CSS, Node 20
- **Backend**: Java 21, Spring Boot 3.2.x, Gradle 8.x
- **Database**: PostgreSQL 16
- **DevOps**: Docker, Docker Compose

## 빠른 시작

### Docker로 전체 실행

```bash
docker compose up -d
```

- **프론트엔드**: http://localhost (로그인: `/login`, 회원가입: `/signup`)
- **백엔드 API**: http://localhost:8080
- **테스트 계정**: admin / password

### 실행 방식별 접속 주소

- **Docker 실행(`docker compose up`)**
  - 프론트엔드: `http://localhost` (80)
  - 백엔드 API: `http://localhost:8080`
- **로컬 개발(`npm run dev`)**
  - 프론트엔드: `http://localhost:3000`
  - 백엔드 API: `http://localhost:8080` (기본 compose 기준)

### 로컬 개발

**백엔드** (PostgreSQL 필요):
```bash
cd backend
# Gradle이 설치되어 있다면:
./gradlew bootRun
# 또는 Docker로 DB만 실행:
docker compose up -d db
```

**프론트엔드**:
```bash
cd frontend
npm install
npm run dev
```

## 프로젝트 구조

```
├── frontend/          # Next.js + React
├── backend/           # Spring Boot
├── db/
│   └── initdb/
│       ├── 00_init.sql           # DB 기본 초기화 스키마
│       └── 89_guardian_seed.sql  # Guardian 샘플 데이터
├── docker-compose.yml
└── README.md
```

## API 엔드포인트

- `POST /api/auth/login` - 로그인 (`loginId`, `password`로 JWT 발급)
- `GET /api/dashboard/metrics` - 대시보드 메트릭 (인증 필요)

## 문서

- 통합 문서 인덱스: [`docs/README.md`](docs/README.md)
- Frontend 명세: [`frontend/docs/frontend-spec.md`](frontend/docs/frontend-spec.md)
- Backend 명세: [`backend/docs/backend-spec.md`](backend/docs/backend-spec.md)

## 포트 충돌 시

`docker-compose.yml`에서 backend 포트를 변경할 수 있습니다 (기본: 8080).


# 1)  컨테이너 삭제 docker compose down
docker compose down --remove-orphans -v --rmi all

# 1-1) postgres:16 쓰는 컨테이너 확인
docker ps -a --filter "ancestor=postgres:16"

# 2) 해당 컨테이너 전부 삭제(실행 중이면 자동 중지 후 삭제)
docker rm -f $(docker ps -aq --filter "ancestor=postgres:16")

# 3) 이미지 삭제
docker rmi postgres:16

# 4) 컨테이너 up
powershell
docker compose build --no-cache; docker compose up -d

command
docker compose build --no-cache&& docker compose up -d

# 프로트만 재시작
docker compose build frontend --no-cache && docker compose up -d frontend


# backend 수동 실행
cd backend

### PowerShell 
cd backend
.\gradlew.bat bootRun 2>&1 | Tee-Object -FilePath .\bootrun.log

netstat -ano | findstr :8080 | findstr LISTENING

# backend 중지
Ctrl + C

# frontend 수동 실행
cd frontend

npm install
cd frontend
npm run dev 2>&1 | Tee-Object -FilePath .\dev.log

# frontend 중지
ctrl + C


# frontend 포트
netstat -ano | findstr :3000

# 포트확인
netstat -ano | findstr :3000 | findstr LISTENING & netstat -ano | findstr :8080 | findstr LISTENING


## windows 방화벽에서 8080 열기
netsh advfirewall firewall add rule name="Open Port 8080" dir=in action=allow protocol=TCP localport=8080

## windows 방화벽에서 8080 삭제
netsh advfirewall firewall delete rule name="Open Port 8080"
