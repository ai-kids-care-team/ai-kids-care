const envApiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL?.trim();

// 로컬 개발 기본값은 IntelliJ 백엔드 디버그 포트(8081) 기준.
const API_BASE_URL_RAW =
  envApiBaseUrl && envApiBaseUrl.length > 0
    ? envApiBaseUrl
    : 'http://localhost:8080/api/v1';

// Spring은 기본적으로 URL path 대소문자를 구분합니다.
// 환경변수에서 `/api/V1` 처럼 들어오는 경우 security matchers(`/api/v1/**`)가 안 맞을 수 있어 정규화합니다.
export const API_BASE_URL = API_BASE_URL_RAW.replace(/\/api\/v1/i, '/api/v1');

// 일부 레거시 엔드포인트(/api) 호출이 남아 있어 /v1 제거 버전도 제공.
export const LEGACY_API_BASE_URL = API_BASE_URL.replace(/\/v1\/?$/, '');
