'use client';

import { useEffect } from 'react';
import { useParams, useRouter, usePathname } from 'next/navigation';
import { useAuth } from './AuthProvider';

/**
 * Per docs/frontend/04-multi-tenant-model.md §5.2 — wraps tenant-scoped pages and enforces:
 *   1. URL has :tenantId
 *   2. caller is authenticated
 *   3. URL's tenantId === JWT's tenantId claim
 *
 * Any violation triggers a redirect to /login with the current path preserved.
 */
export function RequireTenantMatch({ children }: { children: React.ReactNode }) {
  const params = useParams();
  const { state } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  const urlTenantId = typeof params.tenantId === 'string' ? params.tenantId : undefined;

  useEffect(() => {
    if (state.status === 'loading') return;
    if (state.status !== 'authenticated') {
      const redirect = encodeURIComponent(pathname);
      router.replace(`/login?redirect=${redirect}`);
      return;
    }
    if (!urlTenantId || state.claims.tenantId !== urlTenantId) {
      router.replace('/login');
    }
  }, [state, urlTenantId, pathname, router]);

  if (state.status !== 'authenticated' || state.claims.tenantId !== urlTenantId) {
    return <div className="flex items-center justify-center h-screen text-slate-500">Loading…</div>;
  }
  return <>{children}</>;
}
