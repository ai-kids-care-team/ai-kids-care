import { baseApi } from '@/services/apis/base.api';
import type { User } from '../../store/slices/userSlice';

export interface Notification {
  id: string;
  title: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

export const userApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    // 내 프로필 정보 조회
    getUserProfile: build.query<User, void>({
      query: () => '/users/me',
      providesTags: ['User'],
    }),

    // TopBar에서 사용되는 알림 목록 조회 (주기적 폴링 대체용)
    getNotifications: build.query<Notification[], string>({
      query: (kindergartenId) => `/kindergartens/${kindergartenId}/notifications`,
      providesTags: ['Notification'],
      // 응답 데이터를 최신순으로 정렬하는 변환 로직 포함
      transformResponse: (response: { data: Notification[] } | Notification[]) => {
        const data = 'data' in response ? response.data : response;
        return data.sort((a, b) =>
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );
      },
    }),

    // 알림 모두 읽음 처리
    markAllNotificationsAsRead: build.mutation<void, string>({
      query: (kindergartenId) => ({
        url: `/kindergartens/${kindergartenId}/notifications/read-all`,
        method: 'PATCH',
      }),
      // 성공 시 캐시를 무효화하여 getNotifications를 다시 호출하도록 유도
      invalidatesTags: ['Notification'],
    }),
  }),
  overrideExisting: false,
});

export const {
  useGetUserProfileQuery,
  useGetNotificationsQuery,
  useMarkAllNotificationsAsReadMutation
} = userApi;