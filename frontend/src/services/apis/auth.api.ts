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

export type AuthSessionResponse = {
  userId: number;
  loginId: string;
  name?: string;
  effectiveRole: string;
  scopeType: 'PLATFORM' | 'KINDERGARTEN';
  scopeId?: number;
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
  const res = await fetch(`${API_BASE_URL}/auth/register/availability?${params}`, {
    credentials: 'include',
  });
  if (!res.ok) {
    throw new Error(`availability ${res.status}`);
  }
  return res.json() as Promise<RegisterFieldAvailability>;
}

export const authApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    login: build.mutation<AuthSessionResponse, LoginRequest>({
      query: (credentials) => ({
        url: '/auth/login',
        method: 'POST',
        body: credentials,
      }),
      invalidatesTags: ['AuthSession'],
    }),
    session: build.query<AuthSessionResponse, void>({
      query: () => '/auth/session',
      providesTags: ['AuthSession'],
    }),
    logout: build.mutation<void, void>({
      query: () => ({
        url: '/auth/logout',
        method: 'POST',
      }),
      invalidatesTags: ['AuthSession'],
    }),
  }),
  overrideExisting: false,
});

export const {
  useLoginMutation,
  useSessionQuery,
  useLogoutMutation,
} = authApi;
