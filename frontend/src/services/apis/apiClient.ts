import axios from 'axios';
import { API_BASE_URL } from '@/config/api';
import { index as appStore } from '@/store/index';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 1. 요청(Request) 인터셉터: API를 호출하기 직전에 항상 실행됨
apiClient.interceptors.request.use(
  (config) => {
    /* localStorage + Redux: 로그인 직후·하이드레이션 타이밍에 한쪽만 채워질 수 있음 */
    let token: string | null = null;
    if (typeof window !== 'undefined') {
      const isAuthenticated = appStore.getState().user.isAuthenticated;
      /* Redux(현재 세션) 우선 — 예전에 남은 만료 토큰이 localStorage에만 있으면 401이 반복되는 문제 방지 */
      // 게스트(로그아웃) 상태에서는 Authorization 헤더를 아예 붙이지 않음.
      // (localStorage에 남아있는 과거 토큰이 있으면, 백엔드가 허용해도 프론트 UX가 꼬일 수 있음)
      token = isAuthenticated
        ? appStore.getState().user.token ??
          localStorage.getItem('accessToken') ??
          localStorage.getItem('token') ??
          null
        : null;
    }
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`; // 헤더에 토큰 부착
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 2. 응답(Response) 인터셉터: API 응답을 받자마자 실행됨 (에러 핸들링 및 토큰 갱신)
apiClient.interceptors.response.use(
  (response) => response, // 성공한 응답은 그대로 통과
  async (error) => {
    const originalRequest = error.config;
    const isBrowser = typeof window !== 'undefined';

    // 401 Unauthorized 에러(토큰 만료)이고, 아직 재시도한 적이 없는 요청이라면
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true; // 무한 루프 방지용 플래그

      if (!isBrowser) {
        return Promise.reject(error);
      }

      const refreshToken = window.localStorage.getItem('refreshToken');
      /* 리프레시 토큰이 없으면 갱신 시도 없이 그대로 실패 처리.
       * (로그인 API가 refresh를 내려주지 않는 경우 401마다 로그인 창이 뜨는 문제 방지) */
      if (!refreshToken) {
        return Promise.reject(error);
      }

      try {
        // 토큰 갱신 API 호출 (openapi 명세서 기준)
        const { data } = await axios.post(`${API_BASE_URL}/auth/refresh`, {
          refreshToken, // 백엔드 설계에 따라 Header나 Cookie로 보낼 수도 있음
        });

        // 새로 발급받은 토큰을 저장 (신/구 키 모두 동기화)
        window.localStorage.setItem('accessToken', data.accessToken);
        window.localStorage.setItem('token', data.accessToken);
        if (data.refreshToken) {
          window.localStorage.setItem('refreshToken', data.refreshToken);
        }

        // 실패했던 원래 요청의 헤더를 새 토큰으로 교체하고 다시 요청!
        originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
        return apiClient(originalRequest);
      } catch {
        /* 갱신 실패 시 자동 로그아웃·로그인 모달 호출은 하지 않음.
         * 각 화면에서 401 메시지로 처리하고, 로그인은 사용자가 버튼으로 연다. */
        return Promise.reject(error);
      }
    }
    return Promise.reject(error); // 401이 아닌 다른 에러는 그대로 반환
  }
);
