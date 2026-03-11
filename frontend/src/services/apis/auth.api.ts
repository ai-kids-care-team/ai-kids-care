import { baseApi } from '@/services/apis/base.api';

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
    changePassword: build.mutation<void, any>({
      query: (data) => ({
        url: '/auth/password/change',
        method: 'POST',
        body: data,
      }),
    }),
    getCommonCodes: build.query<CommonCodeItem[], string>({
      query: (group) => `/auth/common-codes?group=${encodeURIComponent(group)}`,
    }),
    searchChildren: build.query<ChildLookupItem[], string>({
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
  useChangePasswordMutation,
  useGetCommonCodesQuery,
  useLazySearchChildrenQuery,
} = authApi;