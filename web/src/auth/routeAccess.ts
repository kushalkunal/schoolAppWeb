import type { StaffRole } from './jwt';

/**
 * Single source of truth for per-role screen access. Each role maps to the set of route "areas"
 * (relative to {@code /tenants/{tenantId}}) it may reach; {@code '*'} means every area (admins).
 * Used by BOTH the navigation builder (to hide links) and {@code RequireRouteAccess} (to actually
 * block navigation), so the menu and the guard can never drift apart.
 *
 * The backend remains the real authority — every API call is independently authorized — but this
 * stops a forbidden page from rendering its shell and leaking which modules exist.
 */
const ALL = '*' as const;

export const ROLE_AREAS: Record<StaffRole, readonly string[] | typeof ALL> = {
  SUPER_ADMIN: ALL,
  SCHOOL_OWNER: ALL,
  PRINCIPAL: ALL,
  ADMIN: ALL,
  CLASS_TEACHER: [
    '/dashboard', '/schedule', '/attendance', '/academics', '/homework',
    '/circulars', '/students', '/hr/attendance', '/hr/leave',
  ],
  SUBJECT_TEACHER: [
    '/dashboard', '/schedule', '/attendance', '/academics', '/homework',
    '/circulars', '/students', '/hr/attendance', '/hr/leave',
  ],
  ACCOUNTANT: [
    '/dashboard', '/fees', '/expenses', '/notifications',
    '/circulars', '/hr/attendance', '/hr/leave',
  ],
  LIBRARIAN: [
    '/dashboard', '/library', '/schedule', '/circulars', '/hr/attendance', '/hr/leave',
  ],
  // Read-only auditor: dashboards + read views only (writes 403 at the backend anyway).
  VIEWER: ['/dashboard', '/students', '/attendance', '/academics', '/fees/dashboard'],
};

/**
 * Strips the {@code /tenants/{tenantId}} prefix and returns the area path beginning with '/'.
 * The tenant root itself maps to {@code /dashboard}.
 */
export function tenantRelativePath(pathname: string, tenantId: string): string {
  const base = `/tenants/${tenantId}`;
  if (pathname === base || pathname === `${base}/`) return '/dashboard';
  if (pathname.startsWith(`${base}/`)) return pathname.slice(base.length);
  return pathname;
}

function areaMatches(area: string, relPath: string): boolean {
  return relPath === area || relPath.startsWith(`${area}/`);
}

/** True if {@code role} may access the given tenant-relative path. */
export function canAccessPath(role: StaffRole, relPath: string): boolean {
  const areas = ROLE_AREAS[role];
  if (areas === ALL) return true;
  return areas.some((a) => areaMatches(a, relPath));
}

/** Convenience for an absolute href under the tenant root. */
export function canAccessHref(role: StaffRole, href: string, tenantId: string): boolean {
  return canAccessPath(role, tenantRelativePath(href, tenantId));
}
