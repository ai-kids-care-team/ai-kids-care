import axios from 'axios';

// 환경변수에 설정된 API 주소를 사용하거나 기본값 사용
//const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'https://api.example.com';
// 변경 후
//const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
const API_BASE_URL = 'http://localhost:8080/api/v1';
export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 1. 요청(Request) 인터셉터: API를 호출하기 직전에 항상 실행됨
apiClient.interceptors.request.use(
  (config) => {
    // 로그인 경로별 저장 키 차이를 흡수하기 위해 accessToken/token 모두 확인
    const token = typeof window !== 'undefined'
      ? (localStorage.getItem('accessToken') ?? localStorage.getItem('token'))
      : null;
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
          window.location.href = '/login'; // 로그인 페이지로 쫓아냄
        }
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error); // 401이 아닌 다른 에러는 그대로 반환
  }
);
