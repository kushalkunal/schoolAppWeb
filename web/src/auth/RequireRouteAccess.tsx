'use client';

import { useEffect } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from './AuthProvider';
import { canAccessPath, tenantRelativePath } from './routeAccess';

/**
 * Client-side route guard. When the authenticated role is not permitted to access the current
 * route (per {@link ROLE_AREAS}), it redirects to the tenant dashboard instead of rendering the
 * page. The backend still authorizes every API call independently — this is defense-in-depth plus
 * correct UX (no shell of a screen the role can't use).
 */
export function RequireRouteAccess({
  tenantId,
  children,
}: {
  tenantId: string;
  children: React.ReactNode;
}) {
  const { state } = useAuth();
  const pathname = usePathname() ?? '';
  const router = useRouter();

  // While auth is loading/anonymous, defer to RequireTenantMatch (which handles the login redirect).
  const denied =
    state.status === 'authenticated' &&
    !canAccessPath(state.claims.role, tenantRelativePath(pathname, tenantId));

  useEffect(() => {
    if (denied) {
      router.replace(`/tenants/${tenantId}/dashboard`);
    }
  }, [denied, tenantId, router]);

  if (denied) {
    return (
      <div className="grid place-items-center py-24 text-center">
        <div>
          <p className="text-sm font-medium text-slate-700">
            You don’t have access to this section.
          </p>
          <p className="mt-1 text-xs text-slate-500">Redirecting to your dashboard…</p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
