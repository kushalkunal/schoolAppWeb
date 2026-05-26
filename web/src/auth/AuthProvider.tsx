'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { apiClient } from '@/api/client';
import { decodeJwt, JwtClaims } from './jwt';
import { tokenStorage } from './tokenStorage';

interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: {
    id: string;
    schoolId: string;
    displayName: string;
    role: string;
  };
}

export interface SendOtpInput {
  phone?: string;
  email?: string;
}

export interface VerifyOtpInput {
  phone?: string;
  email?: string;
  otp: string;
}

type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; claims: JwtClaims };

/** Slice 35 — password-login payload. Same identifier semantics as OTP (one of phone/email). */
export interface PasswordLoginInput {
  phone?: string;
  email?: string;
  password: string;
}

interface AuthContextValue {
  state: AuthState;
  sendOtp: (input: SendOtpInput) => Promise<void>;
  loginWithOtp: (input: VerifyOtpInput) => Promise<JwtClaims>;
  /** Slice 35 — sign in with a previously-set password. */
  loginWithPassword: (input: PasswordLoginInput) => Promise<JwtClaims>;
  /** Slice 35 — set or change the caller's password (requires a fresh JWT from OTP). */
  setPassword: (newPassword: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });

  // Hydrate from localStorage on mount. We don't pre-check exp here — the axios
  // interceptor handles refresh-on-401, so a stale-but-still-parseable token is fine.
  useEffect(() => {
    const stored = tokenStorage.read();
    if (!stored) {
      setState({ status: 'anonymous' });
      return;
    }
    try {
      const claims = decodeJwt(stored.accessToken);
      setState({ status: 'authenticated', claims });
    } catch {
      tokenStorage.clear();
      setState({ status: 'anonymous' });
    }
  }, []);

  // Cross-tab token sync — if user logs out in another tab, this one notices via the
  // storage event and updates state. Avoids cached queries leaking after logout.
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const onStorage = (e: StorageEvent) => {
      if (!e.key || !e.key.startsWith('sms.')) return;
      const stored = tokenStorage.read();
      if (!stored) {
        setState({ status: 'anonymous' });
        return;
      }
      try {
        const claims = decodeJwt(stored.accessToken);
        // If tenant changed (different login), reload to drop in-flight queries.
        if (state.status === 'authenticated' && claims.tenantId !== state.claims.tenantId) {
          window.location.reload();
          return;
        }
        setState({ status: 'authenticated', claims });
      } catch {
        setState({ status: 'anonymous' });
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [state]);

  const sendOtp = useCallback(async (input: SendOtpInput) => {
    await apiClient.post('/api/v1/auth/otp/send', input);
  }, []);

  const loginWithOtp = useCallback(async (input: VerifyOtpInput): Promise<JwtClaims> => {
    const res = await apiClient.post('/api/v1/auth/otp/verify', input);
    const auth: AuthResponse = res.data.data;
    tokenStorage.write({
      accessToken: auth.accessToken,
      refreshToken: auth.refreshToken,
      expiresAt: Date.now() + auth.expiresInSeconds * 1000,
    });
    const claims = decodeJwt(auth.accessToken);
    setState({ status: 'authenticated', claims });
    return claims;
  }, []);

  const loginWithPassword = useCallback(async (input: PasswordLoginInput): Promise<JwtClaims> => {
    const res = await apiClient.post('/api/v1/auth/password/login', input);
    const auth: AuthResponse = res.data.data;
    tokenStorage.write({
      accessToken: auth.accessToken,
      refreshToken: auth.refreshToken,
      expiresAt: Date.now() + auth.expiresInSeconds * 1000,
    });
    const claims = decodeJwt(auth.accessToken);
    setState({ status: 'authenticated', claims });
    return claims;
  }, []);

  const setPassword = useCallback(async (newPassword: string) => {
    // Bearer token already attached by axios interceptor.
    await apiClient.post('/api/v1/auth/password/set', { password: newPassword });
  }, []);

  const logout = useCallback(async () => {
    const current = tokenStorage.read();
    try {
      await apiClient.post('/api/v1/auth/logout', {
        refreshToken: current?.refreshToken ?? null,
      });
    } catch {
      // best-effort
    }
    tokenStorage.clear();
    setState({ status: 'anonymous' });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ state, sendOtp, loginWithOtp, loginWithPassword, setPassword, logout }),
    [state, sendOtp, loginWithOtp, loginWithPassword, setPassword, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
