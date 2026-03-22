# Docker + IntelliJ 동시 실행 가이드

## 실행 시 체크 사항

- IntelliJ 백엔드 실행 시 `local` 프로파일 활성화
  - VM 옵션: `-Dspring.profiles.active=local`
  - 또는 환경변수: `SPRING_PROFILES_ACTIVE=local`
- 프론트 로컬 실행 전에 `frontend/.env.local` 값 확인
  - `NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api/v1`
- `npm run dev` 실행 중이었다면 프론트 dev 서버 재시작 (환경변수 반영)
- 브라우저 Network 탭에서 API 호출 주소가 `http://localhost:8081/api/v1/...` 인지 확인
- Docker Compose 실행 시 프론트는 `80`, 백엔드는 `8080` 포트 매핑 상태 확인

## 작업 내용 (기존 → 변경)

~~프론트 API가 코드 곳곳에서 `http://localhost:8080` 하드코딩되어 `npm run dev` 시 IntelliJ 백엔드 `8081`로 자동 연결되지 않음~~

~~도커/로컬 실행 환경 분리 규칙이 없어 같은 코드로 동시에 사용 시 충돌 가능~~

아래와 같이 분리 적용 완료:

- 프론트 API 기준을 `NEXT_PUBLIC_API_BASE_URL` 단일 환경변수로 통일
- 신규 파일 `frontend/src/config/api.ts` 추가
  - `API_BASE_URL` 공통 제공
  - `LEGACY_API_BASE_URL` 공통 제공 (`/v1` 제거 파생)
- 하드코딩 `localhost:8080` 제거
  - `frontend/src/services/apis/base.api.ts`
  - `frontend/src/services/apis/apiClient.ts`
  - `frontend/src/components/context/AuthContext.tsx`
  - `frontend/src/components/auth/model/useSignup.ts`
- 로컬 개발값 정리
  - `frontend/.env.local`: `NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api/v1`
  - `frontend/.env.example`도 동일 기준으로 업데이트
- 도커 빌드값 정리
  - `frontend/Dockerfile`에서 `NEXT_PUBLIC_API_BASE_URL=/api/v1`
  - Docker + Nginx 경유 시 `/api/v1` 상대경로 호출 유지
- 백엔드 로컬 디버그용 설정 추가
  - `backend/src/main/resources/application-local.yml` 생성 (`server.port: 8081`)

## 실행 모드별 기준

- Docker Compose (Cursor):
  - Frontend: `http://localhost` (80)
  - Backend: `http://localhost:8080`
  - Frontend API base: `/api/v1`
- IntelliJ + npm dev:
  - Frontend: `http://localhost:3000`
  - Backend: `http://localhost:8081`
  - Frontend API base: `http://localhost:8081/api/v1`
