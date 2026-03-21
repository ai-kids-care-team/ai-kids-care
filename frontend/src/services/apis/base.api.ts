import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import type { RootState } from '@/store';
import { API_BASE_URL } from '@/config/api';

const defaultApiBaseUrl = API_BASE_URL;

export const baseApi = createApi({
  reducerPath: 'api',
  baseQuery: fetchBaseQuery({
    baseUrl: defaultApiBaseUrl,
    prepareHeaders: (headers: Headers, { getState }: { getState: () => unknown }) => {
      // 1. 먼저 Redux 스토어에서 토큰을 찾습니다.
      let token = (getState() as RootState).user.token;

      // 2. 💡 새로고침으로 인해 Redux가 날아갔다면? 브라우저 저장소에서 찾아옵니다.
      // (Next.js의 SSR 환경 에러를 방지하기 위해 typeof window !== 'undefined' 체크 추가)
      if (!token && typeof window !== 'undefined') {
        // 프로젝트에서 로그인 시 토큰을 저장해둔 방식에 맞게 가져오세요.
        // 예: localStorage.getItem('accessToken') 또는 sessionStorage.getItem('token') 등
        token = localStorage.getItem('token');
      }

      // 3. 토큰이 존재하면 헤더에 주입합니다.
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }

      return headers;
    },
  }),
  // 자동 데이터 갱신을 위한 캐시 태그
  tagTypes: ['User', 'Camera', 'Event', 'Notification', 'Metrics'],
  endpoints: () => ({}),
});