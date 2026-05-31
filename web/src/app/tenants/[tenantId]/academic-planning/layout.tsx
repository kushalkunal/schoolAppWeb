'use client';

/**
 * Academic Planning — dedicated Teacher & Timetable Management module.
 *
 * A single top-level area consolidating what used to be scattered across Teachers / Schedule /
 * Settings: the Live Teaching Monitor, teacher & subject allocations, the timetable planner, and
 * emergency substitute replacement. Admin-only; teachers use their personal Schedule.
 */

import Link from 'next/link';
import { useParams, usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';

export default function AcademicPlanningLayout({ children }: { children: React.ReactNode }) {
  const params = useParams();
  const pathname = usePathname() ?? '';
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const root = `/tenants/${tenantId}/academic-planning`;

  const tabs = [
    { label: 'Live Monitor',        href: `${root}/monitor` },
    { label: 'Teacher Allocations', href: `${root}/allocations` },
    { label: 'Subject Allocations', href: `${root}/subjects` },
    { label: 'Timetable',           href: `${root}/timetable` },
    { label: 'Rooms',               href: `${root}/rooms` },
    { label: 'Replacement',         href: `${root}/replacement` },
    { label: 'History',             href: `${root}/history` },
  ];

  return (
    <RequireRole roles={OWNER_OR_ADMIN}>
      <div className="space-y-4">
        <nav className="-mx-6 flex gap-1 overflow-x-auto border-b border-slate-200 px-6 text-sm">
          {tabs.map((t) => {
            const active = pathname.startsWith(t.href);
            return (
              <Link
                key={t.href}
                href={t.href}
                className={cn(
                  'whitespace-nowrap px-3 py-2 -mb-px border-b-2',
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
    </RequireRole>
  );
}
