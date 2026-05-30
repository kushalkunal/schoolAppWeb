'use client';

import { useAuth } from './AuthProvider';
import type { StaffRole } from './jwt';

interface Props {
  roles: StaffRole[];
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

/**
 * Role-gate. Hides children when the current user's role is not in the allowed set.
 * Per docs/frontend/05-role-matrix.md: "hide, don't disable" — disabled controls leak
 * organisational structure.
 */
export function RequireRole({ roles, children, fallback = null }: Props) {
  const { state } = useAuth();
  if (state.status !== 'authenticated') return <>{fallback}</>;
  return roles.includes(state.claims.role) ? <>{children}</> : <>{fallback}</>;
}

/** Composable predicate for use inside render logic (e.g. conditional table columns). */
export function useHasRole(...allowed: StaffRole[]): boolean {
  const { state } = useAuth();
  return state.status === 'authenticated' && allowed.includes(state.claims.role);
}

// Canonical role groups — match backend's AppRoles constants.
export const OWNER_OR_ADMIN: StaffRole[] = ['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN'];
export const ANY_TEACHER:    StaffRole[] = ['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN', 'CLASS_TEACHER', 'SUBJECT_TEACHER'];
export const FEE_WRITER:     StaffRole[] = ['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN', 'ACCOUNTANT'];
export const LIBRARY_WRITER: StaffRole[] = ['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN', 'LIBRARIAN'];
/** Can edit fee structure (create versions, set class amounts, activate). Accountant is read-only. */
export const FEE_CONFIG_EDITOR: StaffRole[] = ['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN'];
export const ATTENDANCE_WRITER: StaffRole[] = ['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN', 'CLASS_TEACHER'];
export const MARKS_WRITER:      StaffRole[] = ['SCHOOL_OWNER', 'PRINCIPAL', 'ADMIN', 'CLASS_TEACHER', 'SUBJECT_TEACHER'];
