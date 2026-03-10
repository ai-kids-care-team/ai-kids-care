import axios from 'axios';
import { DashboardMetric } from '@/app/types/api'; // 추가: 대시보드 타입 임포트

// 환경변수에 설정된 API 주소를 사용하거나 기본값 사용
//const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'https://api.example.com';
// 변경 후
//const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const API_BASE_URL = 'http://localhost:8080';
export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 1. 요청(Request) 인터셉터: API를 호출하기 직전에 항상 실행됨
apiClient.interceptors.request.use(
  (config) => {
    // 로컬 스토리지에서 액세스 토큰을 꺼냄
    const token = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null;
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

    // 401 Unauthorized 에러(토큰 만료)이고, 아직 재시도한 적이 없는 요청이라면
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true; // 무한 루프 방지용 플래그

      try {
        const refreshToken = localStorage.getItem('refreshToken');
        if (!refreshToken) throw new Error('리프레시 토큰이 없습니다.');

        // 토큰 갱신 API 호출 (openapi 명세서 기준)
        const { data } = await axios.post(`${API_BASE_URL}/v1/auth/refresh`, {
          refreshToken, // 백엔드 설계에 따라 Header나 Cookie로 보낼 수도 있음
        });

        // 새로 발급받은 토큰을 저장
        localStorage.setItem('accessToken', data.accessToken);
        if (data.refreshToken) {
          localStorage.setItem('refreshToken', data.refreshToken);
        }

        // 실패했던 원래 요청의 헤더를 새 토큰으로 교체하고 다시 요청!
        originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
        return apiClient(originalRequest);

      } catch (refreshError) {
        // 리프레시 토큰마저 만료되었거나 에러가 났다면 강제 로그아웃
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login'; // 로그인 페이지로 쫓아냄
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error); // 401이 아닌 다른 에러는 그대로 반환
  }
);

// ============================================================================
// API 호출 함수들
// ============================================================================

/**
 * 대시보드 메트릭 데이터를 가져옵니다.
 */
export const getDashboardMetrics = async (): Promise<DashboardMetric[]> => {
  try {
    // 백엔드 API 엔드포인트에 맞춰 URL('/v1/dashboard/metrics')을 수정할 수 있습니다.
    const response = await apiClient.get<DashboardMetric[]>('/v1/dashboard/metrics');
    return response.data;
  } catch (error) {
    console.error('대시보드 데이터를 가져오는데 실패했습니다:', error);
    throw error; // UI 컴포넌트에서 에러 처리를 할 수 있도록 던져줍니다.
  }
};