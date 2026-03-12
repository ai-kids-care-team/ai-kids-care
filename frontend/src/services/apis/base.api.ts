import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import type { RootState } from '@/store';

export const baseApi = createApi({
  reducerPath: 'api',
  baseQuery: fetchBaseQuery({
    baseUrl: process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1',
    prepareHeaders: (headers, { getState }) => {
      // Redux 스토어에서 토큰을 가져와 헤더에 주입 (기존 AuthContext의 토큰 관리 대체)
      const token = (getState() as RootState).user.token;
      if (token) {
        headers.set('authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
  // 자동 데이터 갱신을 위한 캐시 태그
  tagTypes: ['User', 'Camera', 'Event', 'Notification', 'Metrics'],
  endpoints: () => ({}),
});