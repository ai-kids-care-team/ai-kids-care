import { API_BASE_URL } from '@/config/api';
import { baseApi } from '@/services/apis/base.api';

export type RegisterFieldAvailability = {
  available: boolean;
  message: string | null;
};

type LoginRequest = {
  identifier: string;
  password: string;
  id?: string;
};

type LoginResponse = {
  id?: string;
  loginId?: string;
  role?: string;
  accessToken?: string;
  token?: string;
  refreshToken?: string;
  name?: string;
  email?: string;
  kindergartenId?: number;
};

type CommonRegisterRequest = {
  loginId: string;
  password: string;
  email: string;
  phone: string;
  name: string;
};

export type RegisterRequest =
  | (CommonRegisterRequest & {
      userRole: 'GUARDIAN';
      rrnFirst6: string;
      rrnBack7: string;
      gender: string;
      address: string;
      childRrnFirst6: string;
      childRrnBack7: string;
      relationship: string;
      primaryGuardian: boolean;
    })
  | (CommonRegisterRequest & {
      userRole: 'TEACHER' | 'KINDERGARTEN_ADMIN';
      rrnFirst6: string;
      rrnBack7: string;
      gender: string;
      kindergartenId: number;
      emergencyContactName: string;
      emergencyContactPhone: string;
      level: string;
      staffNo: string;
    })
  | (CommonRegisterRequest & {
      userRole: 'SUPERADMIN';
      department: string;
    });

/** 회원가입: 로그인 ID / 이메일 / 연락처 중복 여부 (포커스 아웃 검사) */
export async function fetchRegisterFieldAvailability(
  field: 'loginId' | 'email' | 'phone',
  value: string
): Promise<RegisterFieldAvailability> {
  const params = new URLSearchParams({ field, value: value.trim() });
  const res = await fetch(`${API_BASE_URL}/auth/register/availability?${params}`);
  if (!res.ok) {
    throw new Error(`availability ${res.status}`);
  }
  return res.json() as Promise<RegisterFieldAvailability>;
}

export type CommonCodeItem = {
  codeId?: number;
  codeGroup: string;
  parentCode?: string | null;
  code: string;
  codeName: string;
  sortOrder: number;
  isActive?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

type CommonCodePageResponse = {
  content?: CommonCodeItem[];
};

export const authApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    login: build.mutation<LoginResponse, LoginRequest>({
      query: (credentials) => ({
        url: '/auth/login',
        method: 'POST',
        body: credentials,
      }),
    }),
    register: build.mutation<void, RegisterRequest>({
      query: (userData) => ({
        url: '/auth/register',
        method: 'POST',
        body: userData,
      }),
    }),
    getCommonCodes: build.query<CommonCodeItem[], string>({
      query: (group) => ({
        url: '/common_codes',
        params: {
          codeGroup: group,
          isActive: true,
          size: 100,
          sort: 'sortOrder,asc',
        },
      }),
      transformResponse: (response: CommonCodePageResponse | CommonCodeItem[]) =>
        Array.isArray(response) ? response : (response.content ?? []),
    }),
  }),
  overrideExisting: false,
});

export const {
  useLoginMutation,
  useRegisterMutation,
  useGetCommonCodesQuery,
} = authApi;
