'use client';

import Link from 'next/link';
import { useParams, usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';
import { type FeatureKey, isFeatureEnabled } from '@/features/featureFlags';

/**
 * Shared horizontal sub-nav for /tenants/[tenantId]/settings/*. Each entry maps to a
 * configuration surface — School profile, Classes, Subjects, Staff. Adding a new
 * settings page is one entry here plus a `page.tsx` in the matching folder.
 */
export default function SettingsLayout({ children }: { children: React.ReactNode }) {
  const params = useParams();
  const pathname = usePathname();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';

  // Each tab can optionally name a global feature-flag that gates it. Schools deploying a
  // slimmer build set NEXT_PUBLIC_FEATURE_X=false to hide the corresponding tab + page.
  const tabs: { label: string; href: string; flag?: FeatureKey }[] = [
    { label: 'School',   href: `/tenants/${tenantId}/settings/school` },
    { label: 'Branding', href: `/tenants/${tenantId}/settings/branding` },
    { label: 'Classes',  href: `/tenants/${tenantId}/settings/classes` },
    { label: 'Subjects', href: `/tenants/${tenantId}/settings/subjects` },
    { label: 'Staff',    href: `/tenants/${tenantId}/settings/staff` },
  ];
  const visibleTabs = tabs.filter((t) => !t.flag || isFeatureEnabled(t.flag));

  return (
    <div className="space-y-4">
      <nav className="border-b border-slate-200 -mx-6 px-6 flex gap-1 text-sm">
        {visibleTabs.map((t) => {
          const active = pathname?.startsWith(t.href);
          return (
            <Link
              key={t.href}
              href={t.href}
              className={cn(
                'px-3 py-2 border-b-2 -mb-px',
                active
                  ? 'border-primary text-primary font-medium'
                  : 'border-transparent text-slate-600 hover:text-slate-900',
              )}
            >
              {t.label}
            </Link>
          );
        })}
      </nav>
      {children}
    </div>
  );
}
