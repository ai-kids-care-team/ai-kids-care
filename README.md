# AI Kids Care

**한국어** | [English](README.en.md) | [中文](README.zh-CN.md)

AI Kids Care는 유치원 환경에서 CCTV, 보호자, 교직원, 아동, 알림, 공지, 감사 편지, 이상 행동 감지 이벤트를 한 흐름으로 관리하기 위한 AI 기반 안전 관리 플랫폼입니다. 현재 저장소는 프론트엔드, 백엔드, 데이터베이스, AI 추론 서비스, 배포 자동화 구성을 함께 포함하는 모노레포 형태입니다.

## 프로젝트 개요

이 프로젝트는 유치원 운영자가 카메라와 반/교실 정보를 관리하고, AI가 감지한 이벤트를 검토하며, 보호자와 교직원에게 필요한 정보를 전달할 수 있도록 설계되었습니다. 주요 업무 흐름은 다음과 같습니다.

- 회원가입, 로그인, JWT 인증, 역할 기반 메뉴 제공
- 유치원, 반, 교실, 아동, 보호자, 교직원 데이터 관리
- CCTV 카메라와 스트림 정보 관리
- AI 이상 감지 세션, 감지 이벤트, 증거 파일, 이벤트 리뷰 관리
- 공지사항과 감사 편지 기능
- Neo4j 기반 아동 관계 그래프 조회
- Pushover와 SMS를 통한 실시간 알림 실험

## 핵심 기능

| 영역 | 내용 |
| --- | --- |
| 인증과 권한 | `GUARDIAN`, `TEACHER`, `KINDERGARTEN_ADMIN`, `PLATFORM_IT_ADMIN`, `SUPERADMIN` 역할, JWT 로그인/갱신/로그아웃, 역할별 메뉴 |
| 유치원 운영 데이터 | 유치원, 반, 교실, 교사, 보호자, 아동, 반 배정, 보호자 관계, 교실 배정 |
| CCTV와 감지 이벤트 | 카메라, 스트림, AI 모델, 감지 세션, 감지 이벤트, 리뷰, 증거 파일 |
| 커뮤니케이션 | 공지사항, 감사 편지, 알림 규칙, 디바이스 토큰, 알림 이력 |
| 그래프 데이터 | Neo4j를 사용한 아동 중심 관계 그래프 |
| AI 추론 | VideoMAE 기반 영상 분류, 파일/경로 예측 API, 실시간 스트림 감지와 알림 실험 |

## 기술 아키텍처

| 계층 | 기술 |
| --- | --- |
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS, Radix UI, Redux Toolkit, Axios |
| Backend | Java 21, Spring Boot 3.2.5, Spring Web, Spring Security, Spring Data JPA, Validation, MapStruct, Springdoc OpenAPI, Neo4j Java Driver |
| Database | PostgreSQL 16, Neo4j 5.19, SQL init scripts, seed data, DBML, ERD diagrams |
| AI | Python, FastAPI, Uvicorn, PyTorch, Transformers VideoMAE, AV/FFmpeg, Pushover, SMS |
| DevOps | Docker, Docker Compose, Nginx, Gradle, Jenkinsfile |

## 디렉터리 구조

```text
.
|-- frontend/             # Next.js UI, pages, components, Redux store, API clients
|-- backend/              # Spring Boot API server
|-- ai/                   # VideoMAE training, inference, serving, stream alert scripts
|-- db/                   # PostgreSQL schema, seed data, Neo4j loader, DB utilities
|-- docs/db/ERD/          # ERD diagrams and rendered images
|-- pg-spring-crud-codegen/  # PostgreSQL schema introspection and Java code generation (relocated from scripts/codegen on 2026-05-29, see ADR-0011)
|-- jenkins/              # Jenkins image and compose helper
|-- docker-compose.yml    # Main stack: PostgreSQL, Neo4j, data loader, backend, frontend
|-- Jenkinsfile           # CI/CD pipeline for compose deployment
`-- README*.md            # Multilingual project documentation
```

## 빠른 시작

루트 `docker-compose.yml`은 PostgreSQL, Neo4j, Neo4j 데이터 로더, Spring Boot 백엔드, Nginx 기반 프론트엔드를 함께 실행합니다.

```bash
docker compose up -d --build
```

실행 후 주요 접속 주소는 다음과 같습니다.

| 서비스 | 주소 |
| --- | --- |
| Frontend | `http://localhost` |
| Backend API | `http://localhost:8080/api/v1` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| Neo4j Browser | `http://localhost:7474` |
| PostgreSQL | `localhost:5432` |

기본 환경 변수는 `docker-compose.yml`에 fallback 값이 정의되어 있습니다. 운영 또는 공유 환경에서는 루트의 `.env.example`을 참고해 `.env`를 만들고 DB 계정, Neo4j 계정, JWT secret을 명시적으로 설정하세요.

```bash
cp .env.example .env
docker compose up -d --build
```

## 로컬 개발

데이터베이스만 Docker로 띄우고 백엔드와 프론트엔드를 로컬 프로세스로 실행할 수 있습니다.

```bash
docker compose up -d db neo4j data-loader
```

백엔드:

```bash
cd backend
./gradlew bootRun
```

Windows PowerShell:

```powershell
cd backend
.\gradlew.bat bootRun
```

프론트엔드:

```bash
cd frontend
npm install
npm run dev
```

프론트엔드 개발 서버는 기본적으로 `http://localhost:3000`에서 실행됩니다. API 기본값은 `http://localhost:8080/api/v1`이며, 필요하면 `frontend/.env.example`을 참고해 `NEXT_PUBLIC_API_BASE_URL`을 설정하세요.

주요 개발 명령:

```bash
# frontend
npm run lint
npm run build

# backend
./gradlew test
./gradlew bootJar
```

## AI 서비스

AI 모듈은 루트 compose와 별도로 실행할 수 있습니다. 기본 추론 API는 FastAPI로 제공되며 `8001` 포트를 사용합니다.

```bash
cd ai
docker compose up -d --build
```

로컬 Python 실행:

```bash
cd ai
python -m venv .venv
source .venv/bin/activate
pip install uv
uv sync --no-dev
export PYTHONPATH=src
python scripts/serve.py
```

Windows PowerShell:

```powershell
cd ai
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install uv
uv sync --no-dev
$env:PYTHONPATH = "src"
python scripts\serve.py
```

AI 서비스 주요 엔드포인트:

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/health` | 모델 경로, 디바이스, label 상태 확인 |
| `POST` | `/predict/path` | 서버에서 접근 가능한 영상 파일 경로로 예측 |
| `POST` | `/predict/upload` | 업로드한 영상 파일로 예측 |

`AI_MODEL_DIR` 기본값은 `outputs/videomae_baseline/best_model`입니다. Docker 이미지와 로컬 실행 모두 실제 모델 파일이 해당 경로에 있어야 추론이 가능합니다. 실시간 스트림 추론과 알림 실험은 `ai/scripts/stream_live_alert_service.py`를 중심으로 구성되어 있습니다.

## 데이터베이스와 문서

- PostgreSQL 스키마와 seed SQL: `db/initdb/`
- DBML 스키마: `db/dbml/schema.dbml`
- ERD 문서: `docs/db/ERD/README.md`
- Neo4j 데이터 로더: `db/ne4j_kindergartens/`
- 코드 생성 도구: `pg-spring-crud-codegen/`（구 `scripts/codegen/`, ADR-0011 참조）

백엔드는 Hibernate `ddl-auto=validate`로 실행되므로, DB 스키마는 애플리케이션 시작 전에 SQL init script로 준비되어 있어야 합니다. 루트 Docker Compose를 사용하면 PostgreSQL 컨테이너 생성 시 `db/initdb`가 적용됩니다.

## 주요 API 영역

백엔드 API는 `/api/v1` 아래에 구성됩니다.

- 인증: `/auth/login`, `/auth/logout`, `/auth/refresh`, `/auth/register`, `/auth/password-resets`
- 운영 데이터: `/users`, `/kindergartens`, `/classes`, `/rooms`, `/children`, `/teachers`, `/guardians`
- CCTV와 AI 이벤트: `/cctv_cameras`, `/camera_streams`, `/ai_models`, `/detection_sessions`, `/detection_events`, `/event_reviews`, `/event_evidence_files`
- 커뮤니케이션: `/announcements`, `/appreciation_letters`, `/notifications`, `/notification_rules`, `/device_tokens`
- 그래프: `/graph/children/{childId}`
- 공통 코드와 메뉴: `/common_codes`, `/menus`

자세한 요청/응답 스키마는 백엔드 실행 후 Swagger UI에서 확인하세요.

## 향후 개발 방식 안내

`2026-05-11`부터 이 프로젝트의 후속 개발에는 Vibe Coding과 AI Agents를 적극적으로 활용합니다. 이 선택은 두 가지 배경에서 비롯됩니다. 첫째, Vibe Coding 기술이 빠르게 발전하면서 1인 개발과 유지보수의 효율을 실질적으로 높일 수 있게 되었습니다. 둘째, 프로젝트가 기존 3인 협업 체제에서 후속 유지보수/개발 인력 1인 체제로 줄어들었기 때문에 AI Agents의 활용이 필요한 선택이 되었습니다.

다만 `2026-05-11` 이전의 커밋, 기능, 기여 통계는 기존 기여자들이 전통적인 협업 개발 방식으로 만든 성과로 이해해 주세요. 이 안내는 앞으로의 개발 방식을 투명하게 구분하기 위한 것이며, 프로젝트 전체가 처음부터 AI Agents로만 생성되었다는 오해를 방지하기 위한 설명입니다.

## 기여도와 역할

아래 통계는 `2026-04-10 00:00:00 +0900` 이전 커밋만 대상으로 하며, 해당 기준에서 마지막으로 포함된 커밋은 `0c6dda6` (`2026-04-08 22:14:27 +0900`)입니다. 이후 커밋과 이 README 정리는 통계에 포함하지 않습니다.

통계 기준:

- Commit 비율: cutoff 이전 전체 407 commits 기준
- Churn 비율: `git numstat`의 added + deleted 합계 기준, 전체 256,644 lines
- 역할: cutoff 이전 커밋 메시지와 디렉터리별 변경 분포를 근거로 정리

| Contributor | Commits | Commit share | Churn | Churn share | 주요 역할과 책임 |
| --- | ---: | ---: | ---: | ---: | --- |
| Zhang Junfan 장준범 | 323 | 79.4% | 186,294 | 72.6% | 프로젝트 책임자, 아키텍트, 주 개발자. 백엔드 API, 데이터 모델, AI 학습/추론 파이프라인, 실시간 알림, Docker/Jenkins 구성 전반을 주도했습니다. |
| korea4050-debug | 63 | 15.5% | 29,716 | 11.6% | 백엔드와 프론트엔드 통합, DB/Neo4j 설정, seed 데이터, 공지사항/인증/이상 감지 흐름 보완을 담당했습니다. |
| deokwoo-han | 21 | 5.2% | 40,634 | 15.8% | 프론트엔드 엔지니어. CCTV 모니터링 대시보드, 감사 편지 화면, 프론트엔드 페이지 수정과 일부 백엔드 연동 보완을 담당했습니다. |
