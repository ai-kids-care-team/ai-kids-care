# Backend 프로젝트 명세서

## 1. 개요
`backend`는 Spring Boot 기반 REST API 서버로, JWT 인증과 대시보드 메트릭 조회 기능을 제공한다.  
PostgreSQL과 JPA를 사용하며 Docker 환경에서 실행 가능하다.

## 2. 기술 스택
- Framework: Spring Boot 3.2.5
- Language: Java 21
- Build: Gradle 8.x
- Security: Spring Security + JWT(io.jsonwebtoken)
- ORM: Spring Data JPA (Hibernate)
- Database: PostgreSQL 16

## 3. 실행 및 빌드
### 3.1 로컬 실행
- 빌드: `./gradlew build`
- 실행: `./gradlew bootRun`

### 3.2 Docker
- `backend/Dockerfile` 멀티 스테이지 빌드 사용
  - Build Stage: `gradle bootJar`
  - Runtime Stage: JRE 이미지에서 `app.jar` 실행

## 4. 환경 변수 명세
`application.yml` 기준:
- `DB_HOST` (default: `localhost`)
- `DB_PORT` (default: `5432`)
- `DB_NAME` (default: `dashboard`)
- `DB_USER` (default: `postgres`)
- `DB_PASSWORD` (default: `postgres`)
- `JWT_SECRET` (기본값 제공, 운영 환경에서는 교체 필수)

## 5. 도메인 모델
### 5.1 User
- 필드: `user_id`, `login_id`, `password_hash`, `status`
- 용도: 인증/인가 사용자 정보 저장

### 5.2 DashboardMetric
- 필드: `id`, `metricName`, `value`, `unit`, `createdAt`
- 용도: 대시보드 그리드/게이지 데이터 제공

## 6. API 명세
### 6.1 인증 API
- Base Path: `/api/auth`
- `POST /login`
  - Request: `loginId`, `password`
  - Response: `token`, `loginId`, `role`
  - 실패 시: `401 Unauthorized`, `{ "error": "..." }`

### 6.2 대시보드 API
- Base Path: `/api/dashboard`
- `GET /metrics`
  - 인증 필요(JWT Bearer)
  - Response: `DashboardMetricDto[]`

## 7. 보안 정책
- `/api/auth/**`: 인증 없이 접근 가능
- `/api/**`: JWT 인증 필수
- Session: Stateless
- CSRF: 비활성화
- CORS 허용 Origin:
  - `http://localhost`
  - `http://frontend`

## 8. 초기 데이터 정책
- `DataInitializer`가 애플리케이션 시작 시 기본 계정을 보장한다.
  - `admin / password` (ADMIN)
  - `user / password` (USER)
- 기존 계정이 있으면 비밀번호를 현재 인코딩 규칙으로 갱신한다.

## 9. 예외 처리 정책
- `RuntimeException`은 전역 핸들러에서 `401 Unauthorized`로 변환한다.
- 응답 포맷: `{ "error": "<message>" }`

## 10. 인프라 연계
- `docker-compose.yml` 기준 서비스 구성:
  - `db`(postgres:16, healthcheck)
  - `backend`(본 서비스)
  - `frontend`(Nginx 정적 서빙 + API 프록시)
- 백엔드는 DB healthcheck 성공 후 기동된다.

## 11. 향후 개선 항목
- Refresh Token 및 토큰 재발급 정책 도입
- 예외 타입 세분화(인증/권한/검증/서버 오류)
- API 버전닝(`/api/v1`) 및 OpenAPI 문서 자동화
