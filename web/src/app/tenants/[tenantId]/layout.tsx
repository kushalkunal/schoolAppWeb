'use client';

import Link from 'next/link';
import { useParams, usePathname } from 'next/navigation';
import {
  LayoutDashboard, Users, CalendarCheck2, Wallet, GraduationCap, Megaphone,
  Settings, LogOut, Bell, Search, ChevronDown, UserPlus, UserCog, BookMarked, BookText, Bus,
  Building2, UtensilsCrossed, Package, FileSpreadsheet, ClipboardList, Stethoscope,
  Receipt, Inbox, CalendarClock, ShieldAlert,
} from 'lucide-react';
import { RequireTenantMatch } from '@/auth/RequireTenantMatch';
import { useAuth } from '@/auth/AuthProvider';
import { BrandLogo } from '@/brand/BrandLogo';
import { useBranding, BrandingProvider } from '@/brand/BrandingProvider';
import { Badge } from '@/components/ui/Badge';
import { ChatbotWidget } from '@/components/ai/ChatbotWidget';
import { cn } from '@/lib/utils';
import { type FeatureKey, isFeatureEnabled } from '@/features/featureFlags';

/**
 * Tenant layout. Re-wraps the BrandingProvider with the tenant's schoolId so the
 * provider fetches per-school overrides on mount.
 */
export default function TenantLayout({ children }: { children: React.ReactNode }) {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  return (
    <RequireTenantMatch>
      <BrandingProvider schoolId={tenantId}>
        <AppShell>{children}</AppShell>
      </BrandingProvider>
    </RequireTenantMatch>
  );
}

interface NavItem {
  label: string;
  href: string;
  // Lucide icons are forwardRef components whose props don't fit a strict ComponentType
  // signature. Use ElementType so we can pass `size` + `className` without TS friction.
  icon: React.ElementType;
  badge?: string;
  /**
   * Optional feature-flag gate. The nav entry only renders when the build-time flag is on.
   * Schools that don't deploy a module hide its sidebar link without forking the component.
   */
  flag?: FeatureKey;
}

function AppShell({ children }: { children: React.ReactNode }) {
  const params = useParams();
  const pathname = usePathname() ?? '';
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const { state, logout } = useAuth();
  const b = useBranding();

  const nav: NavItem[] = ([
    { label: 'Dashboard',  href: `/tenants/${tenantId}/dashboard`,         icon: LayoutDashboard },
    { label: 'Students',   href: `/tenants/${tenantId}/students`,          icon: Users,             flag: 'STUDENTS' },
    { label: 'Attendance', href: `/tenants/${tenantId}/attendance`,        icon: CalendarCheck2,    flag: 'ATTENDANCE' },
    { label: 'Fees',       href: `/tenants/${tenantId}/fees/dashboard`,    icon: Wallet,            flag: 'FEE' },
    { label: 'Academics',  href: `/tenants/${tenantId}/academics/exams`,   icon: GraduationCap,     flag: 'ACADEMICS' },
    { label: 'Admissions', href: `/tenants/${tenantId}/admissions`,        icon: UserPlus,          flag: 'ADMISSIONS_FUNNEL' },
    { label: 'HR',         href: `/tenants/${tenantId}/hr/attendance`,     icon: UserCog,           flag: 'STAFF_ATTENDANCE' },
    { label: 'Library',    href: `/tenants/${tenantId}/library/books`,     icon: BookMarked },
    { label: 'Homework',   href: `/tenants/${tenantId}/homework`,          icon: BookText },
    { label: 'Transport',  href: `/tenants/${tenantId}/transport`,         icon: Bus },
    { label: 'Hostel',     href: `/tenants/${tenantId}/hostel`,            icon: Building2,         flag: 'HOSTEL' },
    { label: 'Cafeteria',  href: `/tenants/${tenantId}/cafeteria`,         icon: UtensilsCrossed,   flag: 'CAFETERIA' },
    { label: 'Inventory',  href: `/tenants/${tenantId}/inventory`,         icon: Package,           flag: 'INVENTORY' },
    { label: 'Import',     href: `/tenants/${tenantId}/imports`,           icon: FileSpreadsheet,   flag: 'BULK_IMPORT' },
    { label: 'Circulars',  href: `/tenants/${tenantId}/circulars`,         icon: Megaphone,         flag: 'CIRCULARS' },
    // Slice 34 — daily-ops modules. Each gated by its own global flag.
    { label: 'Visitors',   href: `/tenants/${tenantId}/visitors`,          icon: ClipboardList,     flag: 'VISITOR_MANAGEMENT' },
    { label: 'Infirmary',  href: `/tenants/${tenantId}/infirmary`,         icon: Stethoscope,       flag: 'INFIRMARY_LOG' },
    { label: 'Incidents',  href: `/tenants/${tenantId}/incidents`,         icon: ShieldAlert,       flag: 'INCIDENT_LOG' },
    { label: 'Expenses',   href: `/tenants/${tenantId}/expenses`,          icon: Receipt,           flag: 'EXPENSE_TRACKING' },
    { label: 'PTM',        href: `/tenants/${tenantId}/ptm`,               icon: CalendarClock,     flag: 'PTM_SCHEDULING' },
    { label: 'Inbox',      href: `/tenants/${tenantId}/inbox`,             icon: Inbox,             flag: 'UNIFIED_INBOX' },
    { label: 'Settings',   href: `/tenants/${tenantId}/settings/school`,   icon: Settings },
  ] as NavItem[]).filter((n) => !n.flag || isFeatureEnabled(n.flag));

  const userName = state.status === 'authenticated' ? state.claims.name : '';
  const userRole = state.status === 'authenticated' ? state.claims.role : '';

  return (
    <div className="min-h-screen flex bg-slate-50">
      {/* ---------------- SIDEBAR ---------------- */}
      <aside className="w-64 shrink-0 border-r border-slate-200 bg-white flex flex-col">
        <div className="h-16 px-5 flex items-center border-b border-slate-200">
          <BrandLogo withWordmark />
        </div>
        <nav className="flex-1 p-3 space-y-1 overflow-y-auto">
          {nav.map((n) => {
            const active = pathname.startsWith(n.href.split('/').slice(0, 4).join('/'));
            return (
              <Link
                key={n.href}
                href={n.href}
                className={cn(
                  'flex items-center gap-2.5 px-3 py-2 rounded-brand text-sm font-medium transition relative',
                  active
                    ? 'bg-primary-soft text-primary'
                    : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900',
                )}
              >
                {/* Active indicator pill on the left edge */}
                {active && <span className="absolute left-0 top-1.5 bottom-1.5 w-1 rounded-r-full bg-primary" />}
                <n.icon size={16} className="shrink-0" />
                <span className="flex-1">{n.label}</span>
                {n.badge && <Badge tone="primary" size="sm">{n.badge}</Badge>}
              </Link>
            );
          })}
        </nav>

        {/* User card at the bottom of the sidebar */}
        <div className="p-3 border-t border-slate-200">
          <div className="flex items-center gap-2.5 p-2 rounded-brand hover:bg-slate-50">
            <div className="w-9 h-9 rounded-full bg-brand-gradient text-primary-fg grid place-items-center font-semibold text-sm">
              {(userName || '?').charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0 flex-1">
              <div className="text-sm font-medium text-slate-900 truncate">{userName || 'Signed in'}</div>
              <div className="text-[11px] text-slate-500 truncate">{prettyRole(userRole)}</div>
            </div>
            <button
              onClick={logout}
              className="text-slate-400 hover:text-danger p-1.5 rounded transition"
              title="Sign out"
              aria-label="Sign out"
            >
              <LogOut size={14} />
            </button>
          </div>
        </div>
      </aside>

      {/* ---------------- MAIN ---------------- */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Topbar — sticky, glassy, with global search + quick actions */}
        <header className="h-16 sticky top-0 z-30 glass border-b border-slate-200/70 px-6 flex items-center justify-between">
          <div className="flex items-center gap-3 text-sm text-slate-500">
            <button className="p-1.5 hover:bg-slate-100 rounded transition" aria-label="Search">
              <Search size={16} />
            </button>
            <span className="hidden lg:inline text-xs">
              <span className="font-medium text-slate-700">{b.schoolName}</span>
              {b.affiliation && <span className="text-slate-400"> · {b.affiliation}</span>}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <button className="relative p-2 hover:bg-slate-100 rounded transition" aria-label="Notifications">
              <Bell size={16} className="text-slate-600" />
              {/* Notification badge placeholder; wired later */}
              <span className="absolute top-1.5 right-1.5 w-1.5 h-1.5 rounded-full bg-danger" />
            </button>
            <div className="hidden md:flex items-center gap-1.5 px-2 py-1 rounded-brand text-xs text-slate-600 border border-slate-200 bg-white">
              {prettyRole(userRole)} <ChevronDown size={12} />
            </div>
          </div>
        </header>

        {/* Page content. The 6-unit horizontal padding stays consistent with PageHeader. */}
        <main className="flex-1 p-6 animate-fade-in">{children}</main>
      </div>

      {/* Floating AI assistant — present on every tenant page. Hidden by the global FE flag
          AND further by the per-tenant backend flag (the API returns FEATURE_DISABLED and the
          widget surfaces that error). */}
      {isFeatureEnabled('AI_CHATBOT') && <ChatbotWidget tenantId={tenantId} />}
    </div>
  );
}

function prettyRole(role: string): string {
  if (!role) return '';
  return role
    .replace(/_/g, ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (m) => m.toUpperCase());
}
