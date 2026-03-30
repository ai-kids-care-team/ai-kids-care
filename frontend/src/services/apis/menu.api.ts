import { baseApi } from '@/services/apis/base.api';

export type MenuItem = {
  menuId: number;
  parentId: number | null;
  menuName: string;
  menuKey: string;
  path: string | null;
  icon: string | null;
  roleType: string;
  sortOrder: number;
};

export const menuApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    getMenus: build.query<MenuItem[], string | void>({
      query: (roleType) => {
        const normalized = (roleType ?? 'ANONYMOUS').toUpperCase();
        return `/menus?roleType=${encodeURIComponent(normalized)}`;
      },
      /** 메뉴는 자주 바뀌지 않음 — 캐시를 길게 유지해 라우트 전환 시 재요청·깜박임을 줄임 */
      keepUnusedDataFor: 86400,
    }),
  }),
  overrideExisting: false,
});

export const { useGetMenusQuery } = menuApi;
