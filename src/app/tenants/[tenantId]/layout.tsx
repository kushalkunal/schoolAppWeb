'use client';

import { useState, useRef, useEffect } from 'react';
import Link from 'next/link';
import { useParams, usePathname } from 'next/navigation';
import {
  LayoutDashboard, Users, CalendarCheck2, Wallet, GraduationCap, Megaphone,
  Settings, LogOut, Bell, Search, ChevronDown, UserPlus, UserCog, BookMarked, BookText, Bus,
  FileSpreadsheet, ClipboardList,
  Receipt, Inbox, CalendarClock, ShieldAlert, School, MessageSquare, MoreHorizontal, X, Plane, KeyRound,
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { Drawer } from 'vaul';
import { RequireTenantMatch } from '@/auth/RequireTenantMatch';
import { RequireRouteAccess } from '@/auth/RequireRouteAccess';
import { canAccessHref } from '@/auth/routeAccess';
import type { StaffRole } from '@/auth/jwt';
import { useAuth } from '@/auth/AuthProvider';
import { BrandLogo } from '@/brand/BrandLogo';
import { useBranding, BrandingProvider } from '@/brand/BrandingProvider';
import { PLATFORM } from '@/brand/branding.config';
import { Badge } from '@/components/ui/Badge';
import { ChatbotWidget } from '@/components/ai/ChatbotWidget';
import { GlobalSearchModal } from '@/components/GlobalSearchModal';
import { cn } from '@/lib/utils';
import { type FeatureKey, isFeatureEnabled } from '@/features/featureFlags';
import { apiPost } from '@/api/client';

/**
 * Tenant layout. Re-wraps the BrandingProvider with the tenant's schoolId so the
 * provider fetches per-school overrides on mount.
 */
export default function TenantLayout({ children }: { children: React.ReactNode }) {
  const { state } = useAuth();
  // Branding loads by the real school UUID (from the JWT), not the cosmetic URL slug.
  const realTenantId = state.status === 'authenticated' ? state.claims.tenantId : undefined;
  return (
    <RequireTenantMatch>
      <BrandingProvider schoolId={realTenantId}>
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

  const role = state.status === 'authenticated' ? state.claims.role : '';
  const isTeacherRole = role === 'CLASS_TEACHER' || role === 'SUBJECT_TEACHER';
  const isAccountant = role === 'ACCOUNTANT';
  const isLibrarian = role === 'LIBRARIAN';
  const isReceptionist = role === 'RECEPTIONIST';

  // Teacher-only nav — simplified view for CLASS_TEACHER / SUBJECT_TEACHER
  const teacherNav: NavItem[] = [
    { label: 'My Dashboard',   href: `/tenants/${tenantId}/dashboard`,          icon: LayoutDashboard },
    { label: 'My Schedule',    href: `/tenants/${tenantId}/schedule`,            icon: CalendarClock },
    { label: 'Attendance',     href: `/tenants/${tenantId}/attendance`,         icon: CalendarCheck2,  flag: 'ATTENDANCE' },
    { label: 'My Attendance',  href: `/tenants/${tenantId}/hr/attendance`,      icon: UserCog,         flag: 'STAFF_ATTENDANCE' },
    { label: 'My Leave',       href: `/tenants/${tenantId}/hr/leave`,           icon: Plane },
    { label: 'Academics',      href: `/tenants/${tenantId}/academics/my-classes`, icon: GraduationCap, flag: 'ACADEMICS' },
    { label: 'Homework',       href: `/tenants/${tenantId}/homework`,           icon: BookText },
    { label: 'Circulars',      href: `/tenants/${tenantId}/circulars`,          icon: Megaphone,       flag: 'CIRCULARS' },
  ];

  // Librarian nav — library-focused view
  const librarianNav: NavItem[] = [
    { label: 'Books',          href: `/tenants/${tenantId}/library/books`,      icon: BookMarked },
    { label: 'Issued Books',   href: `/tenants/${tenantId}/library/issues`,     icon: BookText },
    { label: 'My Schedule',    href: `/tenants/${tenantId}/schedule`,            icon: CalendarClock },
    { label: 'My Attendance',  href: `/tenants/${tenantId}/hr/attendance`,      icon: UserCog,         flag: 'STAFF_ATTENDANCE' },
    { label: 'My Leave',       href: `/tenants/${tenantId}/hr/leave`,           icon: Plane },
    { label: 'Circulars',      href: `/tenants/${tenantId}/circulars`,          icon: Megaphone,       flag: 'CIRCULARS' },
  ];

  // Accountant/cashier nav — fee-focused only
  const accountantNav: NavItem[] = [
    { label: 'Fee Dashboard',  href: `/tenants/${tenantId}/fees/dashboard`,     icon: LayoutDashboard },
    { label: 'Collect Fee',    href: `/tenants/${tenantId}/fees/collect`,        icon: Wallet },
    { label: 'Cash Recon',     href: `/tenants/${tenantId}/fees/cash-recon`,     icon: Receipt },
    { label: 'Fee Defaulters', href: `/tenants/${tenantId}/fees/defaulters`,     icon: Users },
    { label: 'Notifications',  href: `/tenants/${tenantId}/notifications`,       icon: MessageSquare,  flag: 'PARENT_NOTIFICATIONS' },
    { label: 'My Attendance',  href: `/tenants/${tenantId}/hr/attendance`,      icon: UserCog,         flag: 'STAFF_ATTENDANCE' },
    { label: 'My Leave',       href: `/tenants/${tenantId}/hr/leave`,           icon: Plane },
  ];

  // Receptionist / front-desk nav — visitors, admissions enquiries, student lookup, circulars.
  // All target pages already exist; this only renders once the backend issues the RECEPTIONIST role.
  const receptionistNav: NavItem[] = [
    // Visitors is the front-desk home (/dashboard redirects here for this role).
    { label: 'Visitors',       href: `/tenants/${tenantId}/visitors`,            icon: ClipboardList,  flag: 'VISITOR_MANAGEMENT' },
    { label: 'Admissions',     href: `/tenants/${tenantId}/admissions`,          icon: UserPlus,       flag: 'ADMISSIONS_FUNNEL' },
    { label: 'Students',       href: `/tenants/${tenantId}/students`,            icon: Users,          flag: 'STUDENTS' },
    { label: 'Circulars',      href: `/tenants/${tenantId}/circulars`,           icon: Megaphone,      flag: 'CIRCULARS' },
    { label: 'My Attendance',  href: `/tenants/${tenantId}/hr/attendance`,       icon: UserCog,        flag: 'STAFF_ATTENDANCE' },
    { label: 'My Leave',       href: `/tenants/${tenantId}/hr/leave`,            icon: Plane },
  ];

  // Full admin nav — grouped by how a school actually works, not by DB tables.
  // Every route below stays a live, deep-linkable page; this only changes how the
  // sidebar groups and labels them. Each link maps to a distinct top-level area so the
  // active-highlight (which keys off the first 3 path segments) never lights two items.
  const adminGroups: { label: string; items: NavItem[] }[] = [
    { label: '', items: [
      { label: 'Dashboard', href: `/tenants/${tenantId}/dashboard`, icon: LayoutDashboard },
    ] },
    { label: 'Students', items: [
      { label: 'Students',   href: `/tenants/${tenantId}/students`,   icon: Users,         flag: 'STUDENTS' },
      { label: 'Admissions', href: `/tenants/${tenantId}/admissions`, icon: UserPlus,      flag: 'ADMISSIONS_FUNNEL' },
      { label: 'Attendance', href: `/tenants/${tenantId}/attendance`, icon: CalendarCheck2, flag: 'ATTENDANCE' },
    ] },
    { label: 'Teaching', items: [
      { label: 'Homework',        href: `/tenants/${tenantId}/homework`,        icon: BookText },
      { label: 'Exams & Results', href: `/tenants/${tenantId}/academics/exams`, icon: GraduationCap, flag: 'ACADEMICS' },
    ] },
    { label: 'Fees & Finance', items: [
      { label: 'Fees',     href: `/tenants/${tenantId}/fees/dashboard`, icon: Wallet,  flag: 'FEE' },
      { label: 'Expenses', href: `/tenants/${tenantId}/expenses`,       icon: Receipt, flag: 'EXPENSE_TRACKING' },
    ] },
    { label: 'Communication', items: [
      // A4: one home for all parent-facing comms; "Notifications" is the parent-notification
      // log (Slice 35), so it's gated on PARENT_NOTIFICATIONS — not FEE as before.
      { label: 'Announcements', href: `/tenants/${tenantId}/circulars`,     icon: Megaphone,     flag: 'CIRCULARS' },
      { label: 'Messages',      href: `/tenants/${tenantId}/inbox`,         icon: Inbox,         flag: 'UNIFIED_INBOX' },
      { label: 'Notifications', href: `/tenants/${tenantId}/notifications`, icon: MessageSquare, flag: 'PARENT_NOTIFICATIONS' },
    ] },
    { label: 'Operations', items: [
      { label: 'Library',   href: `/tenants/${tenantId}/library/books`, icon: BookMarked },
      { label: 'Transport', href: `/tenants/${tenantId}/transport`,     icon: Bus },
      { label: 'Visitors',  href: `/tenants/${tenantId}/visitors`,      icon: ClipboardList, flag: 'VISITOR_MANAGEMENT' },
      { label: 'Incidents', href: `/tenants/${tenantId}/incidents`,     icon: ShieldAlert,   flag: 'INCIDENT_LOG' },
      { label: 'PTM',       href: `/tenants/${tenantId}/ptm`,           icon: CalendarClock, flag: 'PTM_SCHEDULING' },
    ] },
    { label: 'School Setup', items: [
      // A1 + A3: the Teacher & Timetable engine is one entry (was two top-level items:
      // "Teachers" + "Academic Planning"). Staff onboarding lives under Staff & HR →
      // Teachers tab, so no feature is hidden. Class/Subject/Calendar setup are tabs
      // inside Settings, so a single "Settings" link reaches them all.
      { label: 'Teachers & Timetable', href: `/tenants/${tenantId}/academic-planning/monitor`, icon: CalendarClock },
      { label: 'Staff & HR',           href: `/tenants/${tenantId}/hr/attendance`,             icon: UserCog,         flag: 'STAFF_ATTENDANCE' },
      { label: 'Import Data',          href: `/tenants/${tenantId}/imports`,                   icon: FileSpreadsheet, flag: 'BULK_IMPORT' },
      { label: 'Settings',             href: `/tenants/${tenantId}/settings/school`,           icon: Settings },
    ] },
  ];
  const adminNav: NavItem[] = adminGroups.flatMap((g) => g.items);

  const nav: NavItem[] = (isTeacherRole ? teacherNav : isAccountant ? accountantNav : isLibrarian ? librarianNav : isReceptionist ? receptionistNav : adminNav)
    .filter((n) => !n.flag || isFeatureEnabled(n.flag))
    // Never show a link the role can't actually open. Also closes the gap where VIEWER /
    // SUPER_ADMIN fell through to the full admin nav — they now see only their permitted areas.
    .filter((n) => role === '' || canAccessHref(role as StaffRole, n.href, tenantId));

  // Desktop sidebar: admins see grouped sections; focused roles keep a single flat list.
  const isAdminNav = !isTeacherRole && !isAccountant && !isLibrarian && !isReceptionist;
  const navGroups: { label: string; items: NavItem[] }[] = isAdminNav
    ? adminGroups
        .map((g) => ({
          label: g.label,
          items: g.items.filter(
            (n) => (!n.flag || isFeatureEnabled(n.flag))
              && (role === '' || canAccessHref(role as StaffRole, n.href, tenantId)),
          ),
        }))
        .filter((g) => g.items.length > 0)
    : [{ label: '', items: nav }];

  // Mobile bottom nav: 4 highest-daily-value tabs up front + "More" drawer for the rest.
  // A10: instead of array order (which buried Attendance/Fees), float the areas staff touch
  // every day to the front. Stable sort keeps each role's natural order within the same rank.
  const MOBILE_PRIORITY = ['/dashboard', '/attendance', '/fees', '/students', '/schedule', '/library', '/visitors'];
  const topArea = (href: string) => {
    const rel = href.startsWith(`/tenants/${tenantId}`) ? href.slice(`/tenants/${tenantId}`.length) : href;
    return `/${rel.split('/')[1] ?? ''}`;
  };
  const mobileRank = (href: string) => {
    const i = MOBILE_PRIORITY.indexOf(topArea(href));
    return i === -1 ? 99 : i;
  };
  const primaryMobileNav = [...nav].sort((a, b) => mobileRank(a.href) - mobileRank(b.href)).slice(0, 4);
  const primaryHrefs = new Set(primaryMobileNav.map((n) => n.href));
  // "More" drawer keeps the sidebar's group headers (A10) instead of one flat dump.
  const moreGroups = navGroups
    .map((g) => ({ label: g.label, items: g.items.filter((n) => !primaryHrefs.has(n.href)) }))
    .filter((g) => g.items.length > 0);
  const moreMobileNav = nav.filter((n) => !primaryHrefs.has(n.href));
  const [moreOpen, setMoreOpen] = useState(false);

  // A9: pick the first comms screen this role can actually open for the top-bar bell.
  const bellHref =
    [
      `/tenants/${tenantId}/notifications`,
      `/tenants/${tenantId}/inbox`,
      `/tenants/${tenantId}/circulars`,
    ].find((h) => role === '' || canAccessHref(role as StaffRole, h, tenantId)) ??
    `/tenants/${tenantId}/dashboard`;

  const userName = state.status === 'authenticated' ? state.claims.name : '';
  const userRole = role;

  // Profile dropdown state
  const [profileOpen, setProfileOpen] = useState(false);
  const [changePwdOpen, setChangePwdOpen] = useState(false);
  const [newPwd, setNewPwd] = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [pwdError, setPwdError] = useState('');
  const [pwdLoading, setPwdLoading] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);
  const [sbMenuOpen, setSbMenuOpen] = useState(false);
  const sbMenuRef = useRef<HTMLDivElement>(null);

  // Global search state
  const [globalSearchOpen, setGlobalSearchOpen] = useState(false);

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) {
        setProfileOpen(false);
      }
      if (sbMenuRef.current && !sbMenuRef.current.contains(e.target as Node)) {
        setSbMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  // Ctrl+K / Cmd+K keyboard shortcut
  useEffect(() => {
    function handleKey(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setGlobalSearchOpen((o) => !o);
      }
    }
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, []);

  async function handleChangePwd(e: React.FormEvent) {
    e.preventDefault();
    if (newPwd !== confirmPwd) { setPwdError('Passwords do not match'); return; }
    if (newPwd.length < 8) { setPwdError('Minimum 8 characters'); return; }
    setPwdError(''); setPwdLoading(true);
    try {
      await apiPost('/api/v1/auth/password/set', { password: newPwd });
      setChangePwdOpen(false);
      setNewPwd(''); setConfirmPwd('');
    } catch (err) {
      setPwdError(err instanceof Error ? err.message : 'Failed to change password');
    } finally {
      setPwdLoading(false);
    }
  }

  // Single "User Info" menu — shared by the sidebar user card and the top-bar profile button,
  // so both show the same avatar + name + role + Change password + Sign out.
  const renderUserMenu = (onClose: () => void) => (
    <div className="w-56 bg-white border border-slate-200 rounded-brand shadow-lg py-1 overflow-hidden">
      <div className="flex items-center gap-2.5 px-3 py-2.5 border-b border-slate-100">
        <div className="w-9 h-9 rounded-full bg-brand-gradient text-primary-fg grid place-items-center font-semibold text-sm shrink-0">
          {(userName || '?').charAt(0).toUpperCase()}
        </div>
        <div className="min-w-0">
          <div className="font-medium text-sm text-slate-900 truncate">{userName || 'Unknown'}</div>
          <div className="text-xs text-slate-500 truncate">{prettyRole(userRole)}</div>
        </div>
      </div>
      <button
        onClick={() => { onClose(); setChangePwdOpen(true); }}
        className="flex items-center gap-2 w-full px-3 py-2.5 text-sm text-slate-700 hover:bg-slate-50 transition"
      >
        <KeyRound size={14} className="text-slate-400" /> Change password
      </button>
      <button
        onClick={() => { onClose(); logout(); }}
        className="flex items-center gap-2 w-full px-3 py-2.5 text-sm text-danger hover:bg-danger/5 transition"
      >
        <LogOut size={14} /> Sign out
      </button>
    </div>
  );

  return (
    <div className="min-h-dvh flex bg-slate-50">
      {/* ── SIDEBAR (desktop only) ─────────────────────────────────── */}
      <aside className="hidden sm:flex w-64 shrink-0 border-r border-slate-200 bg-white flex-col">
        <div className="h-16 px-5 flex items-center border-b border-slate-200">
          <BrandLogo withWordmark hideDefaultLogo />
        </div>
        <nav className="flex-1 p-3 space-y-3 overflow-y-auto">
          {navGroups.map((group, gi) => (
            <div key={group.label || `grp-${gi}`} className="space-y-1">
              {group.label && (
                <p className="px-3 pt-2 pb-0.5 text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                  {group.label}
                </p>
              )}
              {group.items.map((n) => {
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
                    {active && <span className="absolute left-0 top-1.5 bottom-1.5 w-1 rounded-r-full bg-primary" />}
                    <n.icon size={16} className="shrink-0" />
                    <span className="flex-1">{n.label}</span>
                    {n.badge && <Badge tone="primary" size="sm">{n.badge}</Badge>}
                  </Link>
                );
              })}
            </div>
          ))}
        </nav>

        {/* User card at the bottom of the sidebar — opens the shared User Info menu */}
        <div className="p-3 border-t border-slate-200">
          <div className="relative" ref={sbMenuRef}>
            {sbMenuOpen && (
              <div className="absolute bottom-full left-0 right-0 mb-2 z-50">
                {renderUserMenu(() => setSbMenuOpen(false))}
              </div>
            )}
            <button
              onClick={() => setSbMenuOpen((o) => !o)}
              className="w-full flex items-center gap-2.5 p-2 rounded-brand hover:bg-slate-50 transition text-left"
              aria-haspopup="menu"
              aria-expanded={sbMenuOpen}
            >
              <div className="w-9 h-9 rounded-full bg-brand-gradient text-primary-fg grid place-items-center font-semibold text-sm shrink-0">
                {(userName || '?').charAt(0).toUpperCase()}
              </div>
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium text-slate-900 truncate">{userName || 'Signed in'}</div>
                <div className="text-[11px] text-slate-500 truncate">{prettyRole(userRole)}</div>
              </div>
              <ChevronDown size={14} className={cn('text-slate-400 shrink-0 transition', sbMenuOpen && 'rotate-180')} />
            </button>
          </div>
          {/* Platform attribution — small + unobtrusive; the school's brand leads above. */}
          <p className="mt-2 text-center text-[10px] text-slate-400">
            <a href={PLATFORM.poweredByUrl} target="_blank" rel="noopener noreferrer" className="underline decoration-slate-300 underline-offset-2 hover:text-slate-600">
              {PLATFORM.poweredBy}
            </a>
          </p>
        </div>
      </aside>

      {/* ── MAIN AREA ─────────────────────────────────────────────── */}
      {/* pb-16 sm:pb-0: reserve 64px for the mobile bottom nav */}
      <div className="flex-1 flex flex-col min-w-0 pb-16 sm:pb-0">
        {/* Topbar */}
        <header className="h-14 sm:h-16 sticky top-0 z-30 glass border-b border-slate-200/70 px-4 sm:px-6 flex items-center justify-between">
          {/* Mobile: show logo. Desktop: show search + school name */}
          <div className="flex items-center gap-3">
            <div className="sm:hidden">
              <BrandLogo withWordmark hideDefaultLogo />
            </div>
            <div className="hidden sm:flex items-center gap-3 text-sm text-slate-500">
              <button
                onClick={() => setGlobalSearchOpen(true)}
                className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-slate-200 bg-white text-slate-500 hover:bg-slate-50 hover:text-slate-700 transition text-xs"
                aria-label="Search students (Ctrl+K)"
              >
                <Search size={14} />
                <span>Search student…</span>
                <kbd className="ml-1 px-1 py-0.5 text-[10px] bg-slate-100 rounded border border-slate-200">⌘K</kbd>
              </button>
              <span className="hidden lg:inline text-xs">
                <span className="font-medium text-slate-700">{b.schoolName}</span>
                {b.affiliation && <span className="text-slate-400"> · {b.affiliation}</span>}
              </span>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {/* A9: the bell now navigates to a real screen the role can open (was a dead button
                with a permanent fake unread dot). Falls back through the comms areas by access. */}
            <Link href={bellHref} className="relative p-2 hover:bg-slate-100 rounded transition" aria-label="Notifications">
              <Bell size={16} className="text-slate-600" />
            </Link>
            {/* Profile dropdown */}
            <div className="relative hidden md:block" ref={profileRef}>
              <button
                onClick={() => setProfileOpen((o) => !o)}
                className="flex items-center gap-1.5 px-2 py-1 rounded-brand text-xs text-slate-600 border border-slate-200 bg-white hover:bg-slate-50 transition"
              >
                {prettyRole(userRole)} <ChevronDown size={12} />
              </button>
              {profileOpen && (
                <div className="absolute right-0 top-full mt-1 z-50">
                  {renderUserMenu(() => setProfileOpen(false))}
                </div>
              )}
            </div>
            {/* Mobile: search icon + avatar */}
            <button
              onClick={() => setGlobalSearchOpen(true)}
              className="sm:hidden p-2 hover:bg-slate-100 rounded transition"
              aria-label="Search students"
            >
              <Search size={16} className="text-slate-600" />
            </button>
            <div className="sm:hidden w-8 h-8 rounded-full bg-brand-gradient text-primary-fg grid place-items-center font-semibold text-xs">
              {(userName || '?').charAt(0).toUpperCase()}
            </div>
          </div>
        </header>

        {/* Page content with slide-up entrance on every route change */}
        <motion.main
          key={pathname}
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
          className="flex-1 p-4 sm:p-6"
        >
          <RequireRouteAccess tenantId={tenantId}>{children}</RequireRouteAccess>
        </motion.main>
      </div>

      {/* ── MOBILE BOTTOM NAV ─────────────────────────────────────── */}
      <nav className="sm:hidden fixed bottom-0 inset-x-0 z-40 bg-white/95 backdrop-blur border-t border-slate-200
                      flex items-center justify-around h-16"
           style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}>
        {primaryMobileNav.map((n) => {
          const active = pathname.startsWith(n.href.split('/').slice(0, 4).join('/'));
          return (
            <Link key={n.href} href={n.href}
              className={cn(
                'flex flex-col items-center gap-0.5 px-3 py-2 rounded-brand min-w-[56px] transition',
                active ? 'text-primary' : 'text-slate-400',
              )}>
              <n.icon size={22} strokeWidth={active ? 2.2 : 1.8} />
              <span className="text-[10px] font-medium leading-none">{n.label.split(' ')[0]}</span>
            </Link>
          );
        })}

        {/* "More" tab — only shown when nav has more than 4 items */}
        {moreMobileNav.length > 0 && (
          <button onClick={() => setMoreOpen(true)}
            className="flex flex-col items-center gap-0.5 px-3 py-2 rounded-brand min-w-[56px] text-slate-400 transition">
            <MoreHorizontal size={22} strokeWidth={1.8} />
            <span className="text-[10px] font-medium leading-none">More</span>
          </button>
        )}
      </nav>

      {/* ── MORE DRAWER (vaul) ─────────────────────────────────────── */}
      <Drawer.Root open={moreOpen} onOpenChange={setMoreOpen}>
        <Drawer.Portal>
          <Drawer.Overlay className="fixed inset-0 bg-black/40 z-50 sm:hidden" />
          <Drawer.Content
            className="sm:hidden fixed bottom-0 inset-x-0 z-50 bg-white rounded-t-2xl
                       flex flex-col outline-none"
            style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
          >
            {/* Drag handle */}
            <div className="flex justify-center pt-3 pb-1">
              <div className="w-10 h-1 rounded-full bg-slate-200" />
            </div>
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-2 border-b border-slate-100">
              <span className="font-semibold text-sm text-slate-700">All sections</span>
              <button onClick={() => setMoreOpen(false)}
                className="p-1.5 rounded-full hover:bg-slate-100 text-slate-500">
                <X size={16} />
              </button>
            </div>
            {/* Nav items — keep the sidebar's group headers so structure survives on mobile */}
            <div className="overflow-y-auto py-2 px-2">
              {moreGroups.map((group, gi) => (
                <div key={group.label || `m-grp-${gi}`} className="mb-1">
                  {group.label && (
                    <p className="px-4 pt-3 pb-1 text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                      {group.label}
                    </p>
                  )}
                  {group.items.map((n) => {
                    const active = pathname.startsWith(n.href.split('/').slice(0, 4).join('/'));
                    return (
                      <Link key={n.href} href={n.href}
                        onClick={() => setMoreOpen(false)}
                        className={cn(
                          'flex items-center gap-3 px-4 py-3.5 rounded-brand text-sm font-medium transition',
                          active ? 'bg-primary-soft text-primary' : 'text-slate-700 hover:bg-slate-50',
                        )}>
                        <n.icon size={18} />
                        <span>{n.label}</span>
                      </Link>
                    );
                  })}
                </div>
              ))}
            </div>
            {/* Sign out */}
            <div className="px-4 py-3 border-t border-slate-100">
              <button onClick={logout}
                className="flex items-center gap-3 w-full px-2 py-3 text-sm text-danger rounded-brand hover:bg-danger/5 transition">
                <LogOut size={18} />
                <span>Sign out</span>
              </button>
            </div>
          </Drawer.Content>
        </Drawer.Portal>
      </Drawer.Root>

      {isFeatureEnabled('AI_CHATBOT') && <ChatbotWidget tenantId={tenantId} />}

      {/* Change password modal */}
      {changePwdOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-white rounded-xl shadow-xl p-6 w-full max-w-sm mx-4">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-slate-900">Change password</h2>
              <button
                onClick={() => { setChangePwdOpen(false); setNewPwd(''); setConfirmPwd(''); setPwdError(''); }}
                className="p-1.5 hover:bg-slate-100 rounded transition text-slate-500"
              >
                <X size={16} />
              </button>
            </div>
            <form onSubmit={handleChangePwd} className="space-y-4">
              <div className="space-y-1">
                <label className="text-sm font-medium text-slate-700">New password</label>
                <input
                  type="password"
                  value={newPwd}
                  onChange={(e) => setNewPwd(e.target.value)}
                  className="w-full rounded border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  placeholder="Minimum 8 characters"
                  required
                  autoFocus
                />
              </div>
              <div className="space-y-1">
                <label className="text-sm font-medium text-slate-700">Confirm password</label>
                <input
                  type="password"
                  value={confirmPwd}
                  onChange={(e) => setConfirmPwd(e.target.value)}
                  className="w-full rounded border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  placeholder="Repeat new password"
                  required
                />
              </div>
              {pwdError && <p className="text-xs text-red-600">{pwdError}</p>}
              <div className="flex gap-2 justify-end pt-1">
                <button
                  type="button"
                  onClick={() => { setChangePwdOpen(false); setNewPwd(''); setConfirmPwd(''); setPwdError(''); }}
                  className="px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100 rounded-brand transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={pwdLoading}
                  className="px-3 py-1.5 text-sm bg-primary text-white rounded-brand hover:bg-primary/90 transition disabled:opacity-50"
                >
                  {pwdLoading ? 'Saving…' : 'Save password'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Global student search modal */}
      <GlobalSearchModal
        tenantId={tenantId}
        open={globalSearchOpen}
        onClose={() => setGlobalSearchOpen(false)}
      />
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
