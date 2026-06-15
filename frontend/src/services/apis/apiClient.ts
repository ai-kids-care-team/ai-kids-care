import axios from 'axios';
import { API_BASE_URL } from '@/config/api';
import { index as appStore } from '@/store/index';
import { logout } from '@/store/slices/userSlice';
import { openLoginModal } from '@/utils/auth-modal';
import { getCsrf } from '@/services/csrf';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  async (config) => {
    const method = String(config.method ?? 'get').toUpperCase();
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      const csrf = await getCsrf();
      config.headers = config.headers ?? {};
      config.headers[csrf.headerName] = csrf.token;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      appStore.dispatch(logout());
      if (typeof window !== 'undefined') {
        openLoginModal();
      }
    }
    return Promise.reject(error);
  }
);
