import { API_BASE_URL } from '@/config/api';
import { baseApi } from '@/services/apis/base.api';

export type RegisterFieldAvailability = {
  available: boolean;
  message: string | null;
};

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
  codeGroup: string;
  parentCode?: string | null;
  code: string;
  codeName: string;
  sortOrder: number;
};

export type ChildLookupItem = {
  childId: number;
  kindergartenId: number;
  classId: number | null;
  className: string | null;
  name: string;
  childNo: string | null;
  birthDate: string | null;
  gender: string | null;
};

export const authApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    login: build.mutation<any, any>({
      query: (credentials) => ({
        url: '/auth/login',
        method: 'POST',
        body: credentials,
      }),
    }),
    register: build.mutation<void, any>({
      query: (userData) => ({
        url: '/auth/register',
        method: 'POST',
        body: userData,
      }),
    }),
    sendVerificationCode: build.mutation<void, { type: string; target: string }>({
      query: (data) => ({
        url: '/auth/verification-codes',
        method: 'POST',
        body: data,
      }),
    }),
    verifyCode: build.mutation<void, { target: string; code: string }>({
      query: (data) => ({
        url: '/auth/verification-codes/verify',
        method: 'POST',
        body: data,
      }),
    }),
    forgotPassword: build.mutation<void, { email: string }>({
      query: (data) => ({
        url: '/auth/password/forgot',
        method: 'POST',
        body: data,
      }),
    }),
    resetPassword: build.mutation<void, any>({
      query: (data) => ({
        url: '/auth/password/reset',
        method: 'POST',
        body: data,
      }),
    }),
    getCommonCodes: build.query<CommonCodeItem[], string>({
      query: (group) => `/auth/common-codes?group=${encodeURIComponent(group)}`,
    }),
    searchChildren: build.query<ChildLookupItem[], string>({
      // 백엔드 버전별 차이 대응: 기본 경로는 /v1/children
      query: (keyword) => `/children?name=${encodeURIComponent(keyword)}`,
    }),
  }),
  overrideExisting: false,
});

export const {
  useLoginMutation,
  useRegisterMutation,
  useSendVerificationCodeMutation,
  useVerifyCodeMutation,
  useForgotPasswordMutation,
  useResetPasswordMutation,
  useGetCommonCodesQuery,
  useLazySearchChildrenQuery,
} = authApi;