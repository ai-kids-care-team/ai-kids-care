import type { AxiosError } from 'axios';

function messageFromResponseBody(data: unknown): string {
  if (!data || typeof data !== 'object') return '';
  const o = data as Record<string, unknown>;
  for (const key of ['message', 'error', 'detail', 'title'] as const) {
    const v = o[key];
    if (typeof v === 'string' && v.trim()) return v.trim();
  }
  return '';
}

/** Axios 응답·일반 Error에서 사용자에게 보여줄 짧은 메시지 추출 */
export function getApiErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'isAxiosError' in err && (err as AxiosError).isAxiosError) {
    const ax = err as AxiosError<unknown>;
    const fromBody = messageFromResponseBody(ax.response?.data);
    if (fromBody) return fromBody;
    if (ax.response?.status === 401) {
      return '로그인이 필요하거나 토큰이 만료되었습니다. 다시 로그인한 뒤 시도해 주세요. (백엔드 주소·포트가 맞는지도 확인해 주세요.)';
    }
    if (ax.response?.status === 404) return '요청한 정보를 찾을 수 없습니다.';
    if (ax.response?.status === 400) return '입력 값을 확인해 주세요.';
    if (ax.response?.status === 403) return '권한이 없습니다.';
    if (ax.response?.status === 500) return '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';
  }
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}
