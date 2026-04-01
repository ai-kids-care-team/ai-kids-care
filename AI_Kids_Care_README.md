## AI Kids Care – 프로젝트 분석 문서

### 1. 개요

**AI Kids Care**는 유치원/어린이집 환경에서 발생하는 **AI 기반 이상 징후 탐지 이벤트(detection events)** 와 **공지/알림(announcements)** 을 통합 관리하는 **React + Spring Boot 기반 풀스택 대시보드 MVP**입니다.  
프론트엔드는 **Next.js App Router**를 사용하고, 백엔드는 **Spring Boot 3 + PostgreSQL**을 기반으로 하며, 전체 서비스는 **Docker Compose**로 손쉽게 구동할 수 있도록 구성되어 있습니다.

이 문서는 기존 `README.md`, `db/README.md`, `docs/db/ERD/README.md`, `frontend` 구조 및 일부 API 코드(`commonCodes.api.ts`)를 바탕으로 작성된 **분석용 통합 README**입니다.

---

### 2. 주요 기능 요약

- **인증 및 계정 관리**
  - 실제 코드 기준으로는 **로그인 전용 라우트 `/login` 페이지는 없고**, 홈 화면 상단 `LoginModal` + `/ (루트)`와 `/(auth)/signup`, `/(auth)/forgot-password`, `/(auth)/reset-password` 등의 조합으로 인증 플로우를 구성하고 있습니다.
  - 백엔드에서 `POST /api/auth/login` 호출 시 `loginId`, `password` 기반 **JWT 발급** (루트 `README.md` 기준) – 프론트에서는 `auth.api.ts` 및 `LoginForm`/`LoginModal`을 통해 이 API를 사용합니다.

- **대시보드 및 메트릭**
  - `GET /api/dashboard/metrics` 엔드포인트로 전체 서비스 상태/통계를 조회 (인증 필요)

- **AI 탐지 이벤트 관리 (`/detectionEvents`)**
  - `frontend/src/app/detectionEvents` 하위에 **목록/상세/등록/수정** 페이지가 존재
    - `page.tsx`: AI 탐지 이벤트 리스트 진입점 (`DetectionEventsListPage` 컴포넌트 사용)
    - `read/page.tsx`: 이벤트 상세 조회
    - `write/page.tsx`: 신규 이벤트 등록
    - `edit/page.tsx`: 기존 이벤트 수정
  - 이벤트 유형 및 상태 등은 **공통 코드(Common Code)** API를 통해 동적으로 조회

- **공지사항 관리 (`/announcements`)**
  - `frontend/src/app/announcements` 하위에 **목록/상세/등록/수정** 화면 구성
  - 운영자/관리자가 유치원/보호자에게 전달할 공지 메시지를 관리

- **공통 코드 관리 (Common Codes)**
  - `frontend/src/services/apis/commonCodes.api.ts`에서 **공통 코드 조회용 API 클라이언트** 제공
  - 예: `/common_codes/code_group/detection_events/parent_code/event_type`
  - 백엔드 응답 포맷이 배열 / 페이징(`content`) / 래핑(`data`) 등 다양한 경우를 모두 호환하도록 방어적으로 구현

---

### 3. 기술 스택

- **Frontend**
  - Next.js `16.1.6` (App Router)
  - React `19.2.3`, React DOM `19.2.3`
  - TypeScript, Tailwind CSS v4
  - 상태 관리: `@reduxjs/toolkit`, `react-redux`
  - UI 컴포넌트: Radix UI, shadcn 계열 유틸(`class-variance-authority`, `tailwind-merge`), `lucide-react`
  - UI/UX 보조: `react-hook-form`, `react-day-picker`, `recharts`, `embla-carousel-react`, `sonner` 등

- **Backend**
  - Java 21
  - Spring Boot 3.2.x
  - Gradle 8.x

- **Database & Infra**
  - PostgreSQL 16
  - Redis (세션/캐시/토큰 등 보조 용도, `db/README.md` 언급)
  - Docker, Docker Compose 기반 컨테이너 오케스트레이션

---

### 4. 디렉터리 구조 개요

루트 `README.md` 및 실제 폴더 구조 기준 요약입니다.

```text
├── frontend/                # Next.js + React 프론트엔드
│   ├── src/
│   │   ├── app/
│   │   │   ├── page.tsx                     # 대시보드/홈 진입 (HomePage)
│   │   │   ├── (auth)/                      # 로그인/회원가입/비밀번호 초기화 등
│   │   │   ├── detectionEvents/             # AI 탐지 이벤트 관련 페이지
│   │   │   └── announcements/               # 공지사항 관련 페이지
│   │   └── services/apis/                   # Axios 기반 API 클라이언트 모음
│   │       └── commonCodes.api.ts           # 공통 코드 조회 API
│   └── README.md                            # Next.js 기본 템플릿 설명
│
├── backend/                 # Spring Boot 백엔드 (코드는 생략, README 기준)
│   └── docs/backend-spec.md # 백엔드 상세 명세 문서(참조 링크)
│
├── db/
│   ├── initdb/
│   │   ├── 00_init.sql                      # 기본 스키마 초기화
│   │   └── 89_guardian_seed.sql             # Guardian 샘플 데이터
│   ├── db_sample/                           # 샘플 데이터/레퍼런스 문서
│   ├── db_timezone_setup/                   # 타임존 설정 관련 문서
│   └── README.md                            # DB/Redis 컨테이너 운영 및 한글/타임존 가이드
│
├── docs/
│   └── db/
│       └── ERD/
│           ├── pics/                        # ERD 이미지들
│           └── README.md                    # 전체/유치원/AI 탐지 프로세스 ERD
│
├── docker-compose.yml
├── README.md                                # 루트 기본 설명(빠른 시작/포트/문서 링크)
└── AI_Kids_Care_README.md                   # (현재 문서) 프로젝트 분석용 README
```

---

### 5. 프론트엔드 구조 및 특징

- **라우팅**
  - Next.js App Router 사용 – `frontend/src/app` 하위 디렉터리 구조에 따라 URL 매핑
  - 주요 경로:
    - `/` → `HomePage` 컴포넌트
    - `/detectionEvents` → AI 탐지 이벤트 목록
    - `/detectionEvents/read`, `/write`, `/edit` → 이벤트 상세/등록/수정
    - `/announcements` 및 `/announcements/*` → 공지사항 목록/상세/등록/수정
    - `/(auth)/signup`, `/(auth)/forgot-password`, `/(auth)/reset-password` → 인증 관련 화면

- **UI/UX**
  - Radix UI + Tailwind CSS 조합으로 **일관된 디자인 시스템** 적용
  - Suspense를 이용해 데이터 로딩 시 `"불러오는 중입니다."` 메시지 노출
  - 폼 처리에 `react-hook-form`을 사용해 유효성 검사/상태 관리를 단순화

- **API 레이어**
  - `frontend/src/services/apis` 안에서 **Axios 기반 API 클라이언트** 정의
  - 예: `commonCodes.api.ts`는 공통 코드 조회 시 응답 스키마 차이를 흡수하는 유틸리티 역할
    - 배열/페이징(`content`), 또는 `data` 래핑을 모두 처리해 프론트엔드 사용성을 높임

---

### 6. 백엔드 및 DB 구조 개요

- **백엔드**
  - Spring Boot 3.2.x / Java 21 / Gradle 8 기반 API 서버
  - 주요 엔드포인트(루트 `README.md` 기준):
    - `POST /api/auth/login` – 로그인 및 JWT 발급
    - `GET /api/dashboard/metrics` – 대시보드 메트릭(보호된 API)
    - 그 외 `common_codes`, `detection_events`, `announcements` 등 도메인별 REST API가 존재하는 것으로 추정

- **Database & ERD**
  - PostgreSQL 16을 사용하며, `db/initdb/*.sql`로 스키마 및 기본 데이터를 초기화
  - `docs/db/ERD/README.md`에 전체 ERD / Kindergarten ERD / AI detection & event process ERD 이미지 정리
  - `89_guardian_seed.sql`을 통해 **보호자(Guardian) 관련 샘플 데이터**를 주입

- **DB 및 Redis 운영 가이드 (`db/README.md`)**
  - `postgresSQL-docker-compose.yml` 기반으로 DB/Redis 컨테이너 관리
  - `local-postgres` / `ai-kids-postgres` 등 컨테이너 이름과 계정 정보 명시
  - 한글 데이터를 안전하게 넣는 방법(UTF-8, PowerShell 코드페이지 설정, `psql` 사용 예시) 설명
  - DB 타임존 설정 가이드 및 관련 문서 링크 포함

---

### 7. 실행 방법 정리

#### 7-1. Docker로 전체 실행 (추천)

루트 `README.md` 기준:

```bash
docker compose up -d
```

- 프론트엔드: `http://localhost` (기본 80 포트, `/login`, `/signup` 사용)
- 백엔드 API: `http://localhost:8080`
- 테스트 계정: `admin / password`

#### 7-2. 로컬 개발 환경 실행

- **백엔드 (PostgreSQL 필요)**

```bash
cd backend
./gradlew bootRun   # Windows: .\gradlew.bat bootRun
```

또는 DB만 Docker로 띄우기:

```bash
docker compose up -d db
```

- **프론트엔드**

```bash
cd frontend
npm install
npm run dev
```

- 접속 주소
  - 프론트엔드: `http://localhost:3000`
  - 백엔드 API: `http://localhost:8080` (기본 compose 기준)

#### 7-3. DB/Redis 컨테이너 관리 (요약)

자세한 내용은 `db/README.md` 참조:

- DB/Redis 재기동:

```bash
cd db
docker compose --env-file .\.env -f .\postgresSQL-docker-compose.yml down -v
docker compose --env-file .\.env -f .\postgresSQL-docker-compose.yml up -d
```

- Guardian 샘플 데이터 주입:

```bash
docker exec -i ai-kids-postgres psql -U kids_user -d kids_postgres_db < db/initdb/89_guardian_seed.sql
```

---

### 8. 개발 시 참고 문서

- **루트 문서 인덱스 / 상세 스펙**
  - 기존 루트 `README.md`에는 아래 문서들이 링크되어 있지만, **현재 리포 기준으로는 파일이 존재하지 않습니다 (추후 작성 필요)**:
    - `docs/README.md`
    - `frontend/docs/frontend-spec.md`
    - `backend/docs/backend-spec.md`

- **DB/ERD 문서 (실제 존재 확인됨)**
  - `docs/db/ERD/README.md`
  - `db/README.md`

---

### 9. 향후 확장 및 개선 포인트 (제안)

- **도메인별 README 보강**
  - `frontend/src/components/**`, `frontend/src/services/apis/**` 등 하위 폴더별 간단한 README를 두어, 신규 참여자가 구조를 빠르게 이해할 수 있도록 개선 가능

- **API 스펙 자동화**
  - Springdoc OpenAPI / Swagger UI 등을 통한 API 문서 자동화로, 프론트엔드/백엔드 간 계약을 명확하게 관리

- **CI/CD 및 품질 관리**
  - GitHub Actions 또는 기타 CI 도구를 사용해 빌드/테스트/린트 자동화
  - E2E 테스트(Playwright, Cypress 등)를 도입하여 주요 플로우(로그인 → 대시보드 → 이벤트/공지 관리)를 검증

이 문서는 현재 시점의 코드/구조를 기반으로 한 분석이며, 추후 도메인/구조 변경 시 함께 업데이트하는 것을 권장합니다.

