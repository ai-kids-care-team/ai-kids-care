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
      // 일부 엔드포인트(`/api/v1/camera_streams`, `/api/v1/detection_events`)가
      // 무인증 호출에 401을 주는 상황이라, Redux 상태가 즉시 복구되지 않아도
      // localStorage에 있는 토큰은 헤더로 붙여서 스트림/이벤트 로딩이 가능하게 한다.
      token =
        appStore.getState().user.token ??
        localStorage.getItem('accessToken') ??
        localStorage.getItem('token') ??
        null;
    }
    if (token) {
      config.headers = config.headers ?? {};
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

    // 401 Unauthorized 에러이고, 아직 재시도한 적이 없는 요청이라면
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true; // 무한 루프 방지용 플래그

      if (!isBrowser) {
        return Promise.reject(error);
      }
      // 백엔드의 `/api/v1/auth/refresh`가 현재 구현되어 있지 않아(Not implemented),
      // 여기서 재시도(refresh)를 하면 원래 문제(인증 실패)가 더 복잡해짐.
      // 따라서 401은 그대로 반환하고, 필요한 경우 화면에서 로그인 UI로 처리하도록 둡니다.
      return Promise.reject(error);
    }
    return Promise.reject(error); // 401이 아닌 다른 에러는 그대로 반환
  }
);
