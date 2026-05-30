'use client';

import Link from 'next/link';
import { useParams, usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';

export default function TeachersLayout({ children }: { children: React.ReactNode }) {
  const params = useParams();
  const pathname = usePathname();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';

  const tabs = [
    { label: 'Onboard',    href: `/tenants/${tenantId}/teachers/onboard` },
    { label: 'Classes',    href: `/tenants/${tenantId}/teachers/classes` },
    { label: 'Timetable',  href: `/tenants/${tenantId}/teachers/timetable` },
  ];

  return (
    <div className="space-y-4">
      <nav className="border-b border-slate-200 -mx-6 px-6 flex gap-1 text-sm">
        {tabs.map((t) => {
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
