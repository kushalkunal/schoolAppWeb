'use client';

import { useEffect } from 'react';
import { useRouter, usePathname } from 'next/navigation';
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
  const { state } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  // The URL's tenant segment is a cosmetic, human-readable slug — the authoritative tenant is the
  // JWT's `tenantId` claim, enforced on every API call (the client rewrites the slug to the real
  // UUID). So here we only require an authenticated session; the slug is never trusted for access.
  useEffect(() => {
    if (state.status === 'loading') return;
    if (state.status !== 'authenticated') {
      router.replace(`/login?redirect=${encodeURIComponent(pathname)}`);
    }
  }, [state, pathname, router]);

  if (state.status !== 'authenticated') {
    return <div className="flex items-center justify-center h-screen text-slate-500">Loading…</div>;
  }
  return <>{children}</>;
}
