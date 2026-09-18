/**
 * localStorage-backed token store. Phase 1 trade-off documented in
 * docs/frontend/01-auth-and-token-lifecycle.md §6: XSS is mitigated by the backend's CSP +
 * a discipline of never embedding user HTML; CSRF is N/A because we're stateless Bearer.
 *
 * Promotion path to httpOnly cookies is a backend change; the frontend interface stays the
 * same shape (read/write/clear), the implementation swaps to cookie-attribute reads.
 */
const ACCESS_KEY  = 'sms.accessToken';
const REFRESH_KEY = 'sms.refreshToken';
const EXP_KEY     = 'sms.expiresAt';

export interface StoredAuth {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;          // epoch ms
}

function isBrowser(): boolean {
  return typeof window !== 'undefined';
}

export const tokenStorage = {
  read(): StoredAuth | null {
    if (!isBrowser()) return null;     // SSR pre-paint
    const accessToken  = localStorage.getItem(ACCESS_KEY);
    const refreshToken = localStorage.getItem(REFRESH_KEY);
    const expiresAtStr = localStorage.getItem(EXP_KEY);
    if (!accessToken || !refreshToken || !expiresAtStr) return null;
    return { accessToken, refreshToken, expiresAt: Number(expiresAtStr) };
  },
  write(a: StoredAuth): void {
    if (!isBrowser()) return;
    localStorage.setItem(ACCESS_KEY,  a.accessToken);
    localStorage.setItem(REFRESH_KEY, a.refreshToken);
    localStorage.setItem(EXP_KEY,     String(a.expiresAt));
  },
  clear(): void {
    if (!isBrowser()) return;
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(EXP_KEY);
  },
};
