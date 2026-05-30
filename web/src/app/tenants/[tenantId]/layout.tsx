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

  const role = state.status === 'authenticated' ? state.claims.role : '';
  const isTeacherRole = role === 'CLASS_TEACHER' || role === 'SUBJECT_TEACHER';
  const isAccountant = role === 'ACCOUNTANT';
  const isLibrarian = role === 'LIBRARIAN';

  // Teacher-only nav — simplified view for CLASS_TEACHER / SUBJECT_TEACHER
  const teacherNav: NavItem[] = [
    { label: 'My Dashboard',   href: `/tenants/${tenantId}/dashboard`,          icon: LayoutDashboard },
    { label: 'My Schedule',    href: `/tenants/${tenantId}/schedule`,            icon: CalendarClock },
    { label: 'Attendance',     href: `/tenants/${tenantId}/attendance`,         icon: CalendarCheck2,  flag: 'ATTENDANCE' },
    { label: 'My Attendance',  href: `/tenants/${tenantId}/hr/attendance`,      icon: UserCog,         flag: 'STAFF_ATTENDANCE' },
    { label: 'My Leave',       href: `/tenants/${tenantId}/hr/leave`,           icon: Plane },
    { label: 'Academics',      href: `/tenants/${tenantId}/academics/exams`,    icon: GraduationCap,   flag: 'ACADEMICS' },
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
    { label: 'Notifications',  href: `/tenants/${tenantId}/notifications`,       icon: MessageSquare,  flag: 'FEE' },
    { label: 'My Attendance',  href: `/tenants/${tenantId}/hr/attendance`,      icon: UserCog,         flag: 'STAFF_ATTENDANCE' },
    { label: 'My Leave',       href: `/tenants/${tenantId}/hr/leave`,           icon: Plane },
  ];

  // Full admin nav
  const adminNav: NavItem[] = [
    { label: 'Dashboard',  href: `/tenants/${tenantId}/dashboard`,         icon: LayoutDashboard },
    { label: 'Students',   href: `/tenants/${tenantId}/students`,          icon: Users,             flag: 'STUDENTS' },
    { label: 'Attendance', href: `/tenants/${tenantId}/attendance`,        icon: CalendarCheck2,    flag: 'ATTENDANCE' },
    { label: 'Fees',       href: `/tenants/${tenantId}/fees/dashboard`,    icon: Wallet,            flag: 'FEE' },
    { label: 'Academics',  href: `/tenants/${tenantId}/academics/exams`,   icon: GraduationCap,     flag: 'ACADEMICS' },
    { label: 'Admissions', href: `/tenants/${tenantId}/admissions`,        icon: UserPlus,          flag: 'ADMISSIONS_FUNNEL' },
    { label: 'HR',         href: `/tenants/${tenantId}/hr/attendance`,     icon: UserCog,           flag: 'STAFF_ATTENDANCE' },
    { label: 'Substitutes',href: `/tenants/${tenantId}/teachers/substitutes`, icon: CalendarClock, flag: 'SUBSTITUTE_TEACHERS' },
    { label: 'Library',    href: `/tenants/${tenantId}/library/books`,     icon: BookMarked },
    { label: 'Homework',   href: `/tenants/${tenantId}/homework`,          icon: BookText },
    { label: 'Transport',  href: `/tenants/${tenantId}/transport`,         icon: Bus },

    { label: 'Import',     href: `/tenants/${tenantId}/imports`,           icon: FileSpreadsheet,   flag: 'BULK_IMPORT' },
    { label: 'Circulars',  href: `/tenants/${tenantId}/circulars`,         icon: Megaphone,         flag: 'CIRCULARS' },    { label: 'Notifications', href: `/tenants/${tenantId}/notifications`, icon: MessageSquare,     flag: 'FEE' },    { label: 'Visitors',   href: `/tenants/${tenantId}/visitors`,          icon: ClipboardList,     flag: 'VISITOR_MANAGEMENT' },

    { label: 'Incidents',  href: `/tenants/${tenantId}/incidents`,         icon: ShieldAlert,       flag: 'INCIDENT_LOG' },
    { label: 'Expenses',   href: `/tenants/${tenantId}/expenses`,          icon: Receipt,           flag: 'EXPENSE_TRACKING' },
    { label: 'PTM',        href: `/tenants/${tenantId}/ptm`,               icon: CalendarClock,     flag: 'PTM_SCHEDULING' },
    { label: 'Inbox',      href: `/tenants/${tenantId}/inbox`,             icon: Inbox,             flag: 'UNIFIED_INBOX' },
    { label: 'Settings',   href: `/tenants/${tenantId}/settings/school`,   icon: Settings },
  ];

  const nav: NavItem[] = (isTeacherRole ? teacherNav : isAccountant ? accountantNav : isLibrarian ? librarianNav : adminNav)
    .filter((n) => !n.flag || isFeatureEnabled(n.flag))
    // Never show a link the role can't actually open. Also closes the gap where VIEWER /
    // SUPER_ADMIN fell through to the full admin nav — they now see only their permitted areas.
    .filter((n) => role === '' || canAccessHref(role as StaffRole, n.href, tenantId));

  // Mobile bottom nav: first 4 tabs + "More" drawer for the rest
  const primaryMobileNav = nav.slice(0, 4);
  const moreMobileNav = nav.slice(4);
  const [moreOpen, setMoreOpen] = useState(false);

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

  // Global search state
  const [globalSearchOpen, setGlobalSearchOpen] = useState(false);

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) {
        setProfileOpen(false);
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

  return (
    <div className="min-h-dvh flex bg-slate-50">
      {/* ── SIDEBAR (desktop only) ─────────────────────────────────── */}
      <aside className="hidden sm:flex w-64 shrink-0 border-r border-slate-200 bg-white flex-col">
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

      {/* ── MAIN AREA ─────────────────────────────────────────────── */}
      {/* pb-16 sm:pb-0: reserve 64px for the mobile bottom nav */}
      <div className="flex-1 flex flex-col min-w-0 pb-16 sm:pb-0">
        {/* Topbar */}
        <header className="h-14 sm:h-16 sticky top-0 z-30 glass border-b border-slate-200/70 px-4 sm:px-6 flex items-center justify-between">
          {/* Mobile: show logo. Desktop: show search + school name */}
          <div className="flex items-center gap-3">
            <div className="sm:hidden">
              <BrandLogo />
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
            <button className="relative p-2 hover:bg-slate-100 rounded transition" aria-label="Notifications">
              <Bell size={16} className="text-slate-600" />
              <span className="absolute top-1.5 right-1.5 w-1.5 h-1.5 rounded-full bg-danger" />
            </button>
            {/* Profile dropdown */}
            <div className="relative hidden md:block" ref={profileRef}>
              <button
                onClick={() => setProfileOpen((o) => !o)}
                className="flex items-center gap-1.5 px-2 py-1 rounded-brand text-xs text-slate-600 border border-slate-200 bg-white hover:bg-slate-50 transition"
              >
                {prettyRole(userRole)} <ChevronDown size={12} />
              </button>
              {profileOpen && (
                <div className="absolute right-0 top-full mt-1 w-56 bg-white border border-slate-200 rounded-brand shadow-lg py-1 z-50">
                  <div className="px-3 py-2.5 border-b border-slate-100">
                    <div className="font-medium text-sm text-slate-900 truncate">{userName || 'Unknown'}</div>
                    <div className="text-xs text-slate-500 mt-0.5">{prettyRole(userRole)}</div>
                  </div>
                  <button
                    onClick={() => { setProfileOpen(false); setChangePwdOpen(true); }}
                    className="flex items-center gap-2 w-full px-3 py-2.5 text-sm text-slate-700 hover:bg-slate-50 transition"
                  >
                    <KeyRound size={14} className="text-slate-400" />
                    Change password
                  </button>
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
            {/* Nav items */}
            <div className="overflow-y-auto py-2 px-2">
              {moreMobileNav.map((n) => {
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
