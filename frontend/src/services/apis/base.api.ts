import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import type { RootState } from '@/store';
import { API_BASE_URL } from '@/config/api';

const defaultApiBaseUrl = API_BASE_URL;

export const baseApi = createApi({
  reducerPath: 'api',
  baseQuery: fetchBaseQuery({
    baseUrl: defaultApiBaseUrl,
    prepareHeaders: (headers: Headers, { getState }: { getState: () => unknown }) => {
      const token = (getState() as RootState).user.token;
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }

      return headers;
    },
  }),
  tagTypes: [],
  endpoints: () => ({}),
});
