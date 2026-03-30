const envApiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL?.trim();
const isRelativeApiBaseUrl = typeof envApiBaseUrl === 'string' && envApiBaseUrl.startsWith('/');

// 로컬 개발 기본값은 IntelliJ 백엔드 디버그 포트(8081) 기준.
export const API_BASE_URL =
  envApiBaseUrl && envApiBaseUrl.length > 0
    ? // 개발 중에는 `/api/v1` 같은 상대 경로가 프록시 없이 404로 떨어질 수 있음.
      // 운영/컨테이너(nginx 프록시)에서는 상대 경로가 정상.
      isRelativeApiBaseUrl
      ? (() => {
          if (typeof window === 'undefined') return envApiBaseUrl;
          // nginx 프록시가 붙는 기본 포트는 보통 80/443.
          // Next dev(3000) 등에서 `/api/v1`로 요청하면 404가 날 수 있어, 그때만 localhost 백엔드로 전환.
          const port = window.location.port;
          const useLocalBackend = port && port !== '80' && port !== '443';
          return useLocalBackend ? 'http://localhost:8080/api/v1' : envApiBaseUrl;
        })()
      : envApiBaseUrl
    : 'http://localhost:8080/api/v1';

// 일부 레거시 엔드포인트(/api) 호출이 남아 있어 /v1 제거 버전도 제공.
export const LEGACY_API_BASE_URL = API_BASE_URL.replace(/\/v1\/?$/, '');
