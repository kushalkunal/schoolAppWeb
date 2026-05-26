'use client';

import Link from 'next/link';
import { useEffect } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { LogOut, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/auth/AuthProvider';

/**
 * Platform-admin shell. Mounted at /platform/*. Only SUPER_ADMIN may pass — anything
 * else is bounced to /login. The tenant-scoped guard (RequireTenantMatch) is deliberately
 * NOT used here because platform users are not bound to a school.
 */
export default function PlatformLayout({ children }: { children: React.ReactNode }) {
  const { state, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (state.status === 'loading') return;
    if (state.status !== 'authenticated') {
      router.replace(`/login?redirect=${encodeURIComponent(pathname)}`);
      return;
    }
    if (state.claims.role !== 'SUPER_ADMIN') {
      router.replace(`/tenants/${state.claims.tenantId}/dashboard`);
    }
  }, [state, pathname, router]);

  if (state.status !== 'authenticated' || state.claims.role !== 'SUPER_ADMIN') {
    return <div className="flex items-center justify-center h-screen text-slate-500">Loading…</div>;
  }

  const nav = [
    { label: 'Tenants',  href: '/platform/tenants' },
    { label: 'Plans',    href: '/platform/plans' },
    { label: 'Features', href: '/platform/features' },
  ];

  return (
    <div className="min-h-screen flex">
      <aside className="w-56 border-r border-slate-200 bg-slate-900 text-slate-100">
        <div className="p-4 font-semibold flex items-center gap-2">
          <ShieldCheck size={18} /> Platform
        </div>
        <nav className="px-2">
          {nav.map((n) => (
            <Link key={n.href} href={n.href}
              className="block px-3 py-2 rounded text-sm text-slate-200 hover:bg-slate-800">
              {n.label}
            </Link>
          ))}
        </nav>
      </aside>
      <div className="flex-1 flex flex-col">
        <header className="h-14 border-b border-slate-200 bg-white px-4 flex items-center justify-between">
          <div className="text-sm text-slate-500">
            Platform admin · <span className="font-medium text-slate-700">{state.claims.name}</span>
          </div>
          <button onClick={logout} className="text-sm text-slate-600 hover:text-slate-900 flex items-center gap-1">
            <LogOut size={14} /> Sign out
          </button>
        </header>
        <main className="flex-1 p-6 bg-slate-50">{children}</main>
      </div>
    </div>
  );
}
