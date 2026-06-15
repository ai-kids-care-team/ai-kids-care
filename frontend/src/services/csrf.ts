import { API_BASE_URL } from '@/config/api';

type CsrfResponse = {
  token: string;
  headerName: string;
};

let cachedCsrf: CsrfResponse | null = null;
let pendingCsrf: Promise<CsrfResponse> | null = null;

export async function getCsrf(): Promise<CsrfResponse> {
  if (cachedCsrf) return cachedCsrf;
  if (pendingCsrf) return pendingCsrf;

  pendingCsrf = fetch(`${API_BASE_URL}/auth/csrf`, {
    credentials: 'include',
    cache: 'no-store',
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`CSRF bootstrap failed with ${response.status}`);
      }
      const body = (await response.json()) as CsrfResponse;
      if (!body.token || !body.headerName) {
        throw new Error('CSRF bootstrap returned an invalid response');
      }
      cachedCsrf = body;
      return body;
    })
    .finally(() => {
      pendingCsrf = null;
    });

  return pendingCsrf;
}

export function clearCsrf(): void {
  cachedCsrf = null;
}
