'use client';

import Link from 'next/link';
import { useParams, usePathname } from 'next/navigation';
import { CalendarCheck2, FileText, Plane, UserCog } from 'lucide-react';
import { PageHeader } from '@/components/ui/PageHeader';
import { cn } from '@/lib/utils';

/**
 * Sub-shell for the HR module. Owns its own page header so individual sub-pages stay
 * focused on data + actions. The three tabs map to the three HR feature flags:
 *   - Attendance  → STAFF_ATTENDANCE
 *   - Leave       → LEAVE_MANAGEMENT
 *   - Payroll     → PAYROLL
 *
 * Tabs render even when a sub-feature isn't on plan; the underlying page surfaces
 * FEATURE_DISABLED via the existing branded empty state.
 */
export default function HrLayout({ children }: { children: React.ReactNode }) {
  const params = useParams();
  const pathname = usePathname() ?? '';
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';

  const tabs = [
    { label: 'Attendance', href: `/tenants/${tenantId}/hr/attendance`, icon: CalendarCheck2 },
    { label: 'Leave',      href: `/tenants/${tenantId}/hr/leave`,      icon: Plane },
    { label: 'Payroll',    href: `/tenants/${tenantId}/hr/payroll`,    icon: FileText },
  ];

  return (
    <div className="space-y-5">
      <PageHeader
        title="Human Resources"
        description="Mark staff attendance, approve leave, generate payslips"
        icon={<UserCog size={18} />}
      />

      {/* Tab strip — matches the look of /settings/* tabs */}
      <nav className="border-b border-slate-200 -mx-6 px-6 flex gap-1 text-sm">
        {tabs.map((t) => {
          const active = pathname.startsWith(t.href);
          return (
            <Link
              key={t.href}
              href={t.href}
              className={cn(
                'inline-flex items-center gap-1.5 px-3 py-2 border-b-2 -mb-px transition',
                active
                  ? 'border-primary text-primary font-medium'
                  : 'border-transparent text-slate-600 hover:text-slate-900',
              )}
            >
              <t.icon size={14} />
              {t.label}
            </Link>
          );
        })}
      </nav>

      {children}
    </div>
  );
}
