import axios, { AxiosError, AxiosRequestConfig } from 'axios';
import { tokenStorage } from '@/auth/tokenStorage';
import { ApiError } from './errors';

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

export const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

// --- Request interceptor: attach bearer token ---
apiClient.interceptors.request.use((config) => {
  const auth = tokenStorage.read();
  if (auth?.accessToken) {
    config.headers.set('Authorization', `Bearer ${auth.accessToken}`);
  }
  return config;
});

// --- Single-flight refresh state ---
let refreshInFlight: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    const current = tokenStorage.read();
    if (!current) throw new Error('no refresh token');
    try {
      // Bare axios so this request doesn't re-enter the interceptor with the dead bearer.
      const res = await axios.post(
        `${BASE_URL}/api/v1/auth/token/refresh`,
        { refreshToken: current.refreshToken },
      );
      const { accessToken, refreshToken, expiresInSeconds } = res.data.data;
      tokenStorage.write({
        accessToken,
        refreshToken,
        expiresAt: Date.now() + expiresInSeconds * 1000,
      });
      return accessToken;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

function redirectToLogin(): void {
  if (typeof window === 'undefined') return;
  tokenStorage.clear();
  const here = window.location.pathname + window.location.search;
  const param = here && here !== '/login'
    ? `?redirect=${encodeURIComponent(here)}`
    : '';
  window.location.assign(`/login${param}`);
}

type Retryable = AxiosRequestConfig & { _retried?: boolean };

// --- Response interceptor: envelope unwrap + 401 refresh-and-retry ---
apiClient.interceptors.response.use(
  (res) => {
    const body = res.data;
    if (body && typeof body === 'object' && 'success' in body && body.success === false) {
      throw ApiError.from(body.error, res.status);
    }
    return res;
  },
  async (err: AxiosError) => {
    const response = err.response;
    const config = err.config as Retryable | undefined;
    if (!response || !config) throw ApiError.network(err);

    const code = (response.data as { error?: { code?: string } } | undefined)?.error?.code;

    // Only retry on TOKEN_EXPIRED, only once.
    if (response.status === 401 && code === 'TOKEN_EXPIRED' && !config._retried) {
      config._retried = true;
      try {
        const fresh = await refreshAccessToken();
        config.headers = { ...(config.headers ?? {}), Authorization: `Bearer ${fresh}` };
        return apiClient.request(config);
      } catch {
        redirectToLogin();
        throw ApiError.from({ code: 'TOKEN_EXPIRED', message: 'Session expired' }, 401);
      }
    }

    if (response.status === 401) {
      redirectToLogin();
    }

    throw ApiError.from(
      (response.data as { error?: { code?: string; message?: string; details?: Record<string, unknown> } } | undefined)?.error,
      response.status,
    );
  },
);

/** Helper for `useQuery` / `useMutation` to get the data payload directly. */
export async function apiGet<T>(path: string, params?: Record<string, unknown>): Promise<T> {
  const res = await apiClient.get(path, { params });
  return res.data.data as T;
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const res = await apiClient.post(path, body);
  return res.data.data as T;
}

export async function apiPut<T>(path: string, body?: unknown): Promise<T> {
  const res = await apiClient.put(path, body);
  return res.data.data as T;
}

export async function apiDelete<T = void>(path: string): Promise<T> {
  const res = await apiClient.delete(path);
  return res.data.data as T;
}
