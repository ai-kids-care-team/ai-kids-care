import {
  createApi,
  fetchBaseQuery,
  type BaseQueryFn,
  type FetchArgs,
  type FetchBaseQueryError,
} from '@reduxjs/toolkit/query/react';
import { API_BASE_URL } from '@/config/api';
import { getCsrf } from '@/services/csrf';

const defaultApiBaseUrl = API_BASE_URL;
const rawBaseQuery = fetchBaseQuery({
  baseUrl: defaultApiBaseUrl,
  credentials: 'include',
});

const baseQueryWithSession: BaseQueryFn<
  string | FetchArgs,
  unknown,
  FetchBaseQueryError
> = async (args, api, extraOptions) => {
  const request = typeof args === 'string' ? { url: args } : args;
  const method = String(request.method ?? 'GET').toUpperCase();
  let sessionRequest: string | FetchArgs = request;

  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    try {
      const csrf = await getCsrf();
      const headers = new Headers(request.headers as HeadersInit | undefined);
      headers.set(csrf.headerName, csrf.token);
      sessionRequest = { ...request, headers };
    } catch (error) {
      return {
        error: {
          status: 'CUSTOM_ERROR',
          error: error instanceof Error ? error.message : 'CSRF bootstrap failed',
        },
      };
    }
  }

  return rawBaseQuery(sessionRequest, api, extraOptions);
};

export const baseApi = createApi({
  reducerPath: 'api',
  baseQuery: baseQueryWithSession,
  tagTypes: ['AuthSession'],
  endpoints: () => ({}),
});
