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
        const normalized = (roleType ?? 'ALL').toUpperCase();
        return `/menus?roleType=${encodeURIComponent(normalized)}`;
      },
    }),
  }),
  overrideExisting: false,
});

export const { useGetMenusQuery } = menuApi;
