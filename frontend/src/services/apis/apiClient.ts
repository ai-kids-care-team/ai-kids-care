import axios from 'axios';
import { API_BASE_URL } from '@/config/api';
import { index as appStore } from '@/store/index';
import { logout } from '@/store/slices/userSlice';
import { openLoginModal } from '@/utils/auth-modal';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config) => {
    const token = appStore.getState().user.token;
    if (token) {
      config.headers = config.headers ?? {};
      config.headers.Authorization = `Bearer ${token}`;
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
