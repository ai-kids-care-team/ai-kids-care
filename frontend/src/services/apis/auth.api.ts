import { API_BASE_URL } from '@/config/api';
import { baseApi } from '@/services/apis/base.api';
import type { FetchBaseQueryError } from '@reduxjs/toolkit/query/react';

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

/** UX-07 비밀번호 관리 — `openspec/changes/wire-password-management/api-contract.md` 냉동 계약과 필드 단위로 일치시킨다. */
export type ChangePasswordRequest = {
  currentPassword: string;
  newPassword: string;
};

export type PasswordResetRequestBody = {
  loginId: string;
};

/** 계약의 `VerificationCodeCreateVO` — 응답은 계정 존재 여부와 무관하게 항상 이 shape(방지 열거). */
export type PasswordResetRequestResponse = {
  challengeId: string;
  expiresAt: string;
};

export type PasswordResetVerifyBody = {
  challengeId: string;
  code: string;
};

/** 계약의 `PasswordResetVerifyVO` — 필드명은 반드시 `resetToken`(고아 VO의 `verificationToken` 아님). */
export type PasswordResetVerifyResponse = {
  resetToken: string;
  expiresAt: string;
};

export type PasswordResetConfirmBody = {
  resetToken: string;
  newPassword: string;
};

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
    /** 로그인 상태 자기 비밀번호 변경. 성공(204) 시 백엔드가 본인 전체 세션을 무효화한다 — 호출부가 `user` reducer 를 비우고 로그인 페이지로 보내야 한다. */
    changePassword: build.mutation<void, ChangePasswordRequest>({
      query: (body) => ({
        url: '/auth/change-password',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['AuthSession'],
    }),
    /** 비로그인 비밀번호 찾기 1단계. 공개 엔드포인트지만 CSRF 는 그대로 강제(baseApi 인터셉터가 처리). 계정 존재 여부와 무관하게 항상 200. */
    passwordResetRequest: build.mutation<PasswordResetRequestResponse, PasswordResetRequestBody>({
      query: (body) => ({
        url: '/auth/password-reset/request',
        method: 'POST',
        body,
      }),
    }),
    /** 2단계: SMS 코드 검증 → 1회용 resetToken 발급. */
    passwordResetVerify: build.mutation<PasswordResetVerifyResponse, PasswordResetVerifyBody>({
      query: (body) => ({
        url: '/auth/password-reset/verify',
        method: 'POST',
        body,
      }),
    }),
    /** 3단계: resetToken 으로 새 비밀번호 확정(200, no body). */
    passwordResetConfirm: build.mutation<void, PasswordResetConfirmBody>({
      query: (body) => ({
        url: '/auth/password-reset/confirm',
        method: 'POST',
        body,
      }),
    }),
  }),
  overrideExisting: false,
});

export const {
  useLoginMutation,
  useSessionQuery,
  useLogoutMutation,
  useChangePasswordMutation,
  usePasswordResetRequestMutation,
  usePasswordResetVerifyMutation,
  usePasswordResetConfirmMutation,
} = authApi;

/**
 * RTK Query 뮤테이션 에러에서 백엔드 `{ error: string }` shape(계약 통일 규격)를 읽어
 * 사용자에게 보여줄 메시지를 추출한다. `getApiErrorMessage`(Axios 전용, `letters/api-error-message.ts`)와
 * 별도인 이유: 여기는 fetchBaseQuery 기반 `FetchBaseQueryError` 를 다룬다.
 */
export function getAuthApiErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'status' in err) {
    const fbqError = err as FetchBaseQueryError;
    const data = fbqError.data;
    if (data && typeof data === 'object' && 'error' in data) {
      const message = (data as { error?: unknown }).error;
      if (typeof message === 'string' && message.trim()) return message.trim();
    }
  }
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}
