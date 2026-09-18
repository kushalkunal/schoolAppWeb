'use client';

import Link from 'next/link';
import { useParams, usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';
import { type FeatureKey, isFeatureEnabled } from '@/features/featureFlags';
import { useHasRole, FEE_CONFIG_EDITOR } from '@/auth/RequireRole';

/**
 * A8: shared sub-nav for /tenants/[tenantId]/fees/*. Previously the fee sub-pages
 * (collect, defaulters, reconcile, reminders, heads, structure) had no visible navigation —
 * a cashier inside "Collect" had no way to jump to "Defaulters". This adds one consistent
 * strip, splitting the daily cashier loop from admin-only setup. No page is changed; this
 * only renders a tab bar above whatever fee page is active.
 */
export default function FeesLayout({ children }: { children: React.ReactNode }) {
  const params = useParams();
  const pathname = usePathname() ?? '';
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  // Heads / Structure are configuration — accountants are read-only there, so hide the
  // setup tabs from them (matches FEE_CONFIG_EDITOR gating on those pages).
  const isConfigEditor = useHasRole(...FEE_CONFIG_EDITOR);

  type Tab = { label: string; href: string; flag?: FeatureKey };
  const daily: Tab[] = [
    { label: 'Dashboard',  href: `/tenants/${tenantId}/fees/dashboard` },
    { label: 'Collect',    href: `/tenants/${tenantId}/fees/collect` },
    { label: 'Defaulters', href: `/tenants/${tenantId}/fees/defaulters` },
    { label: 'Monthly',    href: `/tenants/${tenantId}/fees/monthly` },
    { label: 'Reminders',  href: `/tenants/${tenantId}/fees/reminders` },
    { label: 'Cash Recon', href: `/tenants/${tenantId}/fees/cash-recon`, flag: 'CASH_RECONCILIATION' },
  ];
  const setup: Tab[] = [
    { label: 'Fee Heads', href: `/tenants/${tenantId}/fees/heads` },
    { label: 'Structure', href: `/tenants/${tenantId}/fees/structure`, flag: 'FEE_STRUCTURE' },
  ];

  const tabs = [...daily, ...(isConfigEditor ? setup : [])].filter((t) => !t.flag || isFeatureEnabled(t.flag));
  // Longest-prefix match so /fees/dashboard doesn't also light up on /fees/dashboard/anything
  // while keeping a single active tab.
  const activeHref = tabs
    .map((t) => t.href)
    .filter((h) => pathname === h || pathname.startsWith(`${h}/`))
    .sort((a, b) => b.length - a.length)[0];

  return (
    <div className="space-y-4">
      <nav className="border-b border-slate-200 -mx-6 px-6 flex gap-1 text-sm overflow-x-auto">
        {tabs.map((t) => (
          <Link
            key={t.href}
            href={t.href}
            className={cn(
              'whitespace-nowrap px-3 py-2 border-b-2 -mb-px',
              t.href === activeHref
                ? 'border-primary text-primary font-medium'
                : 'border-transparent text-slate-600 hover:text-slate-900',
            )}
          >
            {t.label}
          </Link>
        ))}
      </nav>
      {children}
    </div>
  );
}
