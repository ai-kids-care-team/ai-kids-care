import { baseApi } from '@/services/apis/base.api';
import type { User } from '../../store/slices/userSlice';

export const userApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    // 내 프로필 정보 조회
    getUserProfile: build.query<User, void>({
      query: () => '/users/me',
      providesTags: ['User'],
    }),
  }),
  overrideExisting: false,
});

export const {
  useGetUserProfileQuery,
} = userApi;