'use client';

import Link from 'next/link';
import { useParams, usePathname } from 'next/navigation';
import { CalendarCheck2, FileText, Plane, School, UserCog } from 'lucide-react';
import { PageHeader } from '@/components/ui/PageHeader';
import { cn } from '@/lib/utils';
import { useHasRole, OWNER_OR_ADMIN } from '@/auth/RequireRole';

/**
 * Sub-shell for the HR module. Owns its own page header so individual sub-pages stay
 * focused on data + actions. Tabs:
 *   - Attendance  → STAFF_ATTENDANCE
 *   - Leave       → LEAVE_MANAGEMENT
 *   - Payroll     → PAYROLL
 *   - Teachers    → admin/principal only (staff management moved here from top nav)
 */
export default function HrLayout({ children }: { children: React.ReactNode }) {
  const params = useParams();
  const pathname = usePathname() ?? '';
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const isAdmin = useHasRole(...OWNER_OR_ADMIN);

  const tabs = [
    { label: 'Attendance', href: `/tenants/${tenantId}/hr/attendance`, icon: CalendarCheck2 },
    { label: 'Leave',      href: `/tenants/${tenantId}/hr/leave`,      icon: Plane },
    { label: 'Payroll',    href: `/tenants/${tenantId}/hr/payroll`,    icon: FileText },
    ...(isAdmin ? [{ label: 'Teachers', href: `/tenants/${tenantId}/hr/teachers`, icon: School }] : []),
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
