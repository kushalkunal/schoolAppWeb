/** JWT claim shape — mirrors backend's JwtService.JwtClaims. */
export type StaffRole =
  | 'SUPER_ADMIN' | 'SCHOOL_OWNER' | 'PRINCIPAL' | 'ADMIN'
  | 'CLASS_TEACHER' | 'SUBJECT_TEACHER' | 'ACCOUNTANT' | 'VIEWER';

export interface JwtClaims {
  sub: string;       // staffId UUID
  tenantId: string;  // school UUID
  role: StaffRole;
  name: string;
  iat: number;
  exp: number;
}

/**
 * Decodes a JWT without verifying the signature — the server already did that. Only used
 * to read claims after the backend has issued the token.
 */
export function decodeJwt(token: string): JwtClaims {
  const parts = token.split('.');
  if (parts.length !== 3) throw new Error('Not a JWT');
  const payload = parts[1]!;
  // base64url → base64.
  const b64 = payload.replace(/-/g, '+').replace(/_/g, '/');
  const padded = b64 + '==='.slice((b64.length + 3) % 4);
  const json = typeof window === 'undefined'
    ? Buffer.from(padded, 'base64').toString('utf-8')
    : decodeURIComponent(
        atob(padded)
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
  return JSON.parse(json) as JwtClaims;
}
