import axios from 'axios';
import { API_BASE_URL } from '@/config/api';
import { index as appStore } from '@/store/index';
import { openLoginModal } from '@/utils/auth-modal';

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

      try {
        if (!isBrowser) {
          throw new Error('브라우저 환경이 아니어서 토큰 갱신을 수행할 수 없습니다.');
        }

        const refreshToken = window.localStorage.getItem('refreshToken');
        if (!refreshToken) throw new Error('리프레시 토큰이 없습니다.');

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

      } catch (refreshError) {
        // 리프레시 토큰마저 만료되었거나 에러가 났다면 강제 로그아웃
        if (isBrowser) {
          window.localStorage.removeItem('accessToken');
          window.localStorage.removeItem('token');
          window.localStorage.removeItem('refreshToken');
          openLoginModal();
        }
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error); // 401이 아닌 다른 에러는 그대로 반환
  }
);
