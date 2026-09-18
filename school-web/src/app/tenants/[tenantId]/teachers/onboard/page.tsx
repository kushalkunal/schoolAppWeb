'use client';

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { MailCheck, Plus, Trash2, ChevronRight, Save, X, Edit2, UserCog } from 'lucide-react';
import { schoolApi } from '@/api/endpoints/school';
import { hrApi } from '@/api/endpoints/hr';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { Badge } from '@/components/ui/Badge';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { useHasRole } from '@/auth/RequireRole';
import { cn } from '@/lib/utils';
import type { InviteTeacherRequest, LeaveType, StaffProfile, StaffResponse, UpdateStaffRequest } from '@/types/domain';

const TEACHER_ROLES = [
  { value: 'CLASS_TEACHER',   label: 'Class Teacher' },
  { value: 'SUBJECT_TEACHER', label: 'Subject Teacher' },
  { value: 'LIBRARIAN',       label: 'Librarian' },
] as const;

const GENDERS = ['MALE', 'FEMALE', 'OTHER'];

function SectionHeading({ children }: { children: React.ReactNode }) {
  return (
    <div className="pt-1 text-xs font-semibold uppercase tracking-wide text-slate-400 border-t border-slate-100 mt-1">
      {children}
    </div>
  );
}

const ROLE_LABELS: Record<string, string> = {
  CLASS_TEACHER: 'Class Teacher',
  SUBJECT_TEACHER: 'Subject Teacher',
  LIBRARIAN: 'Librarian',
  ACCOUNTANT: 'Accountant',
  ADMIN: 'Admin',
  PRINCIPAL: 'Principal',
  SCHOOL_OWNER: 'School Owner',
};

export default function TeacherOnboardPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [inviteOpen, setInviteOpen] = useState(false);
  const [selected, setSelected] = useState<StaffResponse | null>(null);
  const isAdmin = useHasRole(...OWNER_OR_ADMIN);

  const staffQ = useQuery({
    queryKey: ['staff', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    enabled: !!tenantId,
  });

  const teachers = staffQ.data?.filter(
    (s) => ['CLASS_TEACHER', 'SUBJECT_TEACHER', 'LIBRARIAN'].includes(s.role),
  ) ?? [];

  // Also load sections to show class teacher assignments
  const sectionsQ = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: !!tenantId,
    staleTime: 5 * 60_000,
  });

  // Map staffId → section label(s) they are class teacher of
  const classTeacherMap = new Map<string, string[]>();
  (sectionsQ.data ?? []).forEach((cls) => {
    cls.sections.forEach((sec) => {
      if (sec.classTeacherId) {
        const existing = classTeacherMap.get(sec.classTeacherId) ?? [];
        existing.push(`${cls.name}–${sec.name}`);
        classTeacherMap.set(sec.classTeacherId, existing);
      }
    });
  });

  const deactivate = useMutation({
    mutationFn: (staffId: string) => schoolApi.deactivateStaff(tenantId, staffId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['staff', tenantId] }),
  });

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-semibold">Teachers &amp; Staff</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Click any row to view or edit details. Invite teachers by email — they receive a temporary password.
          </p>
        </div>
        <RequireRole roles={OWNER_OR_ADMIN}>
          <Button onClick={() => setInviteOpen(true)}>
            <Plus size={16} className="mr-1" /> Invite teacher
          </Button>
        </RequireRole>
      </div>

      {staffQ.isLoading && <Spinner />}
      {staffQ.isError && <ErrorBanner error={staffQ.error} onRetry={() => staffQ.refetch()} />}
      {deactivate.isError && <ErrorBanner error={deactivate.error} />}

      {teachers.length === 0 && !staffQ.isLoading && (
        <div className="text-center py-12 text-slate-500">
          <MailCheck size={32} className="mx-auto mb-3 text-slate-300" />
          <p className="font-medium">No teachers yet</p>
          <p className="text-sm">Invite your first teacher to get started.</p>
        </div>
      )}

      {teachers.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-600 text-xs uppercase">
              <tr>
                <th className="text-left px-4 py-2 font-medium">Name</th>
                <th className="text-left px-4 py-2 font-medium">Role</th>
                <th className="text-left px-4 py-2 font-medium">Email</th>
                <th className="text-left px-4 py-2 font-medium">Phone</th>
                <th className="text-left px-4 py-2 font-medium">Class assigned</th>
                <th className="text-left px-4 py-2 font-medium">Status</th>
                <th className="text-right px-4 py-2 font-medium"></th>
              </tr>
            </thead>
            <tbody>
              {teachers.map((t) => (
                <tr
                  key={t.id}
                  className="border-t border-slate-100 hover:bg-slate-50 cursor-pointer"
                  onClick={() => setSelected(t)}
                >
                  <td className="px-4 py-2.5 font-medium text-slate-900">{t.displayName}</td>
                  <td className="px-4 py-2.5 text-slate-600">{ROLE_LABELS[t.role] ?? t.role}</td>
                  <td className="px-4 py-2.5 text-slate-600">{t.email ?? '—'}</td>
                  <td className="px-4 py-2.5 text-slate-600">{t.phone ?? '—'}</td>
                  <td className="px-4 py-2.5 text-slate-500 text-xs">
                    {classTeacherMap.get(t.id)?.join(', ') ?? '—'}
                  </td>
                  <td className="px-4 py-2.5">
                    {t.mustResetPassword ? (
                      <Badge tone="warning" size="sm">Pending reset</Badge>
                    ) : (
                      <Badge tone="success" size="sm">Active</Badge>
                    )}
                  </td>
                  <td className="px-4 py-2.5 text-right" onClick={(e) => e.stopPropagation()}>
                    <RequireRole roles={['SCHOOL_OWNER', 'PRINCIPAL']}>
                      {t.active && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            if (confirm(`Remove ${t.displayName}? They will lose login access.`)) {
                              deactivate.mutate(t.id);
                            }
                          }}
                        >
                          <Trash2 size={14} />
                        </Button>
                      )}
                    </RequireRole>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Teacher detail / edit drawer */}
      {selected && (
        <TeacherDetailDrawer
          tenantId={tenantId}
          staff={selected}
          sections={(sectionsQ.data ?? []).flatMap((cls) =>
            cls.sections.map((s) => ({ ...s, className: cls.name, classId: cls.id })),
          )}
          allStaff={staffQ.data ?? []}
          isAdmin={isAdmin}
          onClose={() => setSelected(null)}
          onUpdated={(updated) => {
            qc.invalidateQueries({ queryKey: ['staff', tenantId] });
            qc.invalidateQueries({ queryKey: ['classes', tenantId] });
            setSelected(updated);
          }}
        />
      )}

      <InviteTeacherModal
        open={inviteOpen}
        tenantId={tenantId}
        onClose={() => setInviteOpen(false)}
        onCreated={() => {
          setInviteOpen(false);
          qc.invalidateQueries({ queryKey: ['staff', tenantId] });
        }}
      />
    </div>
  );
}

// ─── Teacher Detail Drawer ────────────────────────────────────────────────
interface SectionFlat {
  id: string;
  name: string;
  className: string;
  classId: string;
  classTeacherId?: string | null;
}

function TeacherDetailDrawer({
  tenantId, staff, sections, allStaff, isAdmin, onClose, onUpdated,
}: {
  tenantId: string;
  staff: StaffResponse;
  sections: SectionFlat[];
  allStaff: StaffResponse[];
  isAdmin: boolean;
  onClose: () => void;
  onUpdated: (s: StaffResponse) => void;
}) {
  const qc = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<UpdateStaffRequest>({
    firstName: staff.firstName,
    lastName:  staff.lastName ?? '',
    phone:     staff.phone ?? '',
    email:     staff.email ?? '',
    gender:    staff.gender ?? '',
    dateOfJoining: staff.dateOfJoining ?? '',
    role:      staff.role as UpdateStaffRequest['role'],
  });

  const update = useMutation({
    mutationFn: () => schoolApi.updateStaff(tenantId, staff.id, form),
    onSuccess: (updated) => {
      setEditing(false);
      onUpdated(updated);
    },
  });

  // Class teacher assignment
  const mySection = sections.find((s) => s.classTeacherId === staff.id);
  const [assignSectionId, setAssignSectionId] = useState(mySection?.id ?? '');

  const assignCT = useMutation({
    mutationFn: (sectionId: string) =>
      schoolApi.assignClassTeacher(tenantId, sectionId, { staffId: staff.id }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['classes', tenantId] });
    },
  });

  const classTeacherSections = sections.filter((s) => s.classTeacherId === staff.id);

  return (
    <div className="fixed inset-0 z-50 flex">
      {/* Backdrop */}
      <div className="flex-1 bg-black/30" onClick={onClose} />

      {/* Panel */}
      <div className="w-full max-w-lg bg-white shadow-xl flex flex-col overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">{staff.displayName}</h2>
            <p className="text-sm text-slate-500">{ROLE_LABELS[staff.role] ?? staff.role}</p>
          </div>
          <div className="flex items-center gap-2">
            {isAdmin && !editing && (
              <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
                <Edit2 size={13} className="mr-1" /> Edit
              </Button>
            )}
            <button onClick={onClose} className="p-1.5 hover:bg-slate-100 rounded text-slate-400">
              <X size={16} />
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-5 space-y-5">
          {update.isError && <ErrorBanner error={update.error} />}
          {assignCT.isError && <ErrorBanner error={assignCT.error} />}

          {editing ? (
            /* ── Edit form ── */
            <form
              onSubmit={(e) => { e.preventDefault(); update.mutate(); }}
              className="space-y-4"
            >
              <div className="grid grid-cols-2 gap-3">
                <Input
                  label="First name"
                  value={form.firstName ?? ''}
                  required
                  onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                />
                <Input
                  label="Last name"
                  value={form.lastName ?? ''}
                  onChange={(e) => setForm({ ...form, lastName: e.target.value })}
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <Input
                  label="Email"
                  type="email"
                  value={form.email ?? ''}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                />
                <Input
                  label="Phone"
                  type="tel"
                  value={form.phone ?? ''}
                  onChange={(e) => setForm({ ...form, phone: e.target.value })}
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <label className="block">
                  <span className="text-sm text-slate-700 mb-1 inline-block">Gender</span>
                  <select
                    value={form.gender ?? ''}
                    onChange={(e) => setForm({ ...form, gender: e.target.value })}
                    className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
                  >
                    <option value="">— select —</option>
                    {GENDERS.map((g) => (
                      <option key={g} value={g}>{g.charAt(0) + g.slice(1).toLowerCase()}</option>
                    ))}
                  </select>
                </label>
                <Input
                  label="Date of joining"
                  type="date"
                  value={form.dateOfJoining ?? ''}
                  onChange={(e) => setForm({ ...form, dateOfJoining: e.target.value })}
                />
              </div>
              <label className="block">
                <span className="text-sm text-slate-700 mb-1 inline-block">Role</span>
                <select
                  value={form.role ?? staff.role}
                  onChange={(e) => setForm({ ...form, role: e.target.value as UpdateStaffRequest['role'] })}
                  className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
                  disabled={staff.role === 'PRINCIPAL'}
                >
                  {TEACHER_ROLES.map((r) => (
                    <option key={r.value} value={r.value}>{r.label}</option>
                  ))}
                </select>
              </label>
              <div className="flex justify-end gap-2 pt-1">
                <Button variant="secondary" type="button" onClick={() => setEditing(false)} disabled={update.isPending}>
                  Cancel
                </Button>
                <Button type="submit" disabled={update.isPending}>
                  <Save size={13} className="mr-1" />
                  {update.isPending ? 'Saving…' : 'Save changes'}
                </Button>
              </div>
            </form>
          ) : (
            /* ── View mode ── */
            <>
              <section>
                <h3 className="text-xs font-semibold text-slate-500 uppercase mb-3">Personal details</h3>
                <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
                  <DetailRow label="Full name"    value={staff.displayName} />
                  <DetailRow label="Email"        value={staff.email} />
                  <DetailRow label="Phone"        value={staff.phone} />
                  <DetailRow label="Gender"       value={staff.gender} />
                  <DetailRow label="Date of joining" value={staff.dateOfJoining} />
                  <DetailRow label="Role"         value={ROLE_LABELS[staff.role] ?? staff.role} />
                  <DetailRow
                    label="Account status"
                    value={staff.mustResetPassword ? 'Pending password reset' : 'Active'}
                  />
                  <DetailRow label="Date of birth"     value={pv(staff.profile, 'dateOfBirth')} />
                  <DetailRow label="Address"           value={pv(staff.profile, 'address')} />
                  <DetailRow label="Emergency contact" value={pv(staff.profile, 'emergencyContact')} />
                </dl>
              </section>

              <ProfileSection title="Professional details" profile={staff.profile} fields={[
                ['designation', 'Designation'], ['qualification', 'Qualification'],
                ['experienceYears', 'Experience (yrs)'], ['employeeCode', 'Employee code'],
                ['employmentType', 'Employment type'],
              ]} />
              <ProfileSection title="Identity (masked)" profile={staff.profile} fields={[
                ['aadhaarNumber', 'Aadhaar'], ['panNumber', 'PAN'],
              ]} />
              <ProfileSection title="Bank details (masked)" profile={staff.profile} fields={[
                ['bankName', 'Bank'], ['bankAccountNumber', 'Account no.'], ['ifscCode', 'IFSC'],
              ]} />

              {/* Leave balances — auto-seeded on onboarding; Principal/Admin can adjust */}
              <LeaveBalancesPanel tenantId={tenantId} staffId={staff.id} canEdit={isAdmin} />

              {/* Class teacher assignment section */}
              {staff.role === 'CLASS_TEACHER' && (
                <section className="pt-2 border-t border-slate-100">
                  <h3 className="text-xs font-semibold text-slate-500 uppercase mb-3">Class teacher assignment</h3>
                  {classTeacherSections.length > 0 ? (
                    <div className="text-sm text-slate-700 mb-3">
                      Currently assigned to:{' '}
                      <span className="font-medium">
                        {classTeacherSections.map((s) => `${s.className} — Section ${s.name}`).join(', ')}
                      </span>
                    </div>
                  ) : (
                    <p className="text-sm text-slate-500 mb-3">Not assigned to any section yet.</p>
                  )}
                  {isAdmin && (
                    <div className="flex items-center gap-2">
                      <select
                        value={assignSectionId}
                        onChange={(e) => setAssignSectionId(e.target.value)}
                        className="flex-1 rounded border border-slate-300 px-3 py-2 text-sm"
                      >
                        <option value="">— select section —</option>
                        {sections.map((s) => (
                          <option key={s.id} value={s.id}>
                            {s.className} — Section {s.name}
                            {s.classTeacherId && s.classTeacherId !== staff.id
                              ? ` (${allStaff.find((st) => st.id === s.classTeacherId)?.displayName ?? 'assigned'})`
                              : ''}
                          </option>
                        ))}
                      </select>
                      <Button
                        size="sm"
                        disabled={!assignSectionId || assignCT.isPending}
                        onClick={() => assignSectionId && assignCT.mutate(assignSectionId)}
                      >
                        <UserCog size={13} className="mr-1" />
                        {assignCT.isPending ? 'Assigning…' : 'Assign'}
                      </Button>
                    </div>
                  )}
                </section>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div>
      <dt className="text-xs text-slate-500">{label}</dt>
      <dd className="font-medium text-slate-800 mt-0.5">{value ?? '—'}</dd>
    </div>
  );
}

/** Reads a value out of the staff profile JSONB as a display string. */
function pv(profile: Record<string, unknown> | undefined, key: string): string | undefined {
  const v = profile?.[key];
  return v === null || v === undefined || v === '' ? undefined : String(v);
}

/** A grouped profile section; renders nothing if every field is empty. */
function ProfileSection({
  title, profile, fields,
}: { title: string; profile: Record<string, unknown> | undefined; fields: [string, string][] }) {
  const rows = fields.filter(([k]) => pv(profile, k) !== undefined);
  if (rows.length === 0) return null;
  return (
    <section className="pt-2 border-t border-slate-100">
      <h3 className="text-xs font-semibold text-slate-500 uppercase mb-3">{title}</h3>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
        {rows.map(([k, label]) => <DetailRow key={k} label={label} value={pv(profile, k)} />)}
      </dl>
    </section>
  );
}

const LEAVE_LABELS: Record<LeaveType, string> = {
  CASUAL: 'Casual', SICK: 'Sick', EARNED: 'Earned', UNPAID: 'Unpaid',
  MATERNITY: 'Maternity', PATERNITY: 'Paternity', COMP_OFF: 'Comp-off', OTHER: 'Other',
};

/** Leave balances for a staff member (entitled / used / left). Principal/Admin can edit entitled days. */
function LeaveBalancesPanel({ tenantId, staffId, canEdit }: { tenantId: string; staffId: string; canEdit: boolean }) {
  const qc = useQueryClient();
  const year = new Date().getFullYear();
  const balancesQ = useQuery({
    queryKey: ['leave-balances', tenantId, staffId, year],
    queryFn: () => hrApi.listLeaveBalances(tenantId, staffId, year),
    enabled: !!tenantId && !!staffId,
  });
  const [draft, setDraft] = useState<Record<string, string>>({});

  const save = useMutation({
    mutationFn: ({ leaveType, entitledDays }: { leaveType: LeaveType; entitledDays: number }) =>
      hrApi.updateLeaveBalance(tenantId, staffId, leaveType, { entitledDays }, year),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['leave-balances', tenantId, staffId, year] }),
  });

  return (
    <section className="pt-2 border-t border-slate-100">
      <h3 className="text-xs font-semibold text-slate-500 uppercase mb-3">Leave balances · {year}</h3>
      {balancesQ.isLoading && <Spinner />}
      {balancesQ.isError && <ErrorBanner error={balancesQ.error} onRetry={() => balancesQ.refetch()} />}
      {save.isError && <ErrorBanner error={save.error} />}
      {balancesQ.data && (
        <table className="w-full text-sm">
          <thead><tr className="text-xs uppercase text-slate-400 text-left">
            <th className="py-1.5">Type</th><th className="py-1.5 text-right">Entitled</th>
            <th className="py-1.5 text-right">Used</th><th className="py-1.5 text-right">Left</th>
            {canEdit && <th className="py-1.5" />}
          </tr></thead>
          <tbody className="divide-y divide-slate-50">
            {balancesQ.data.map((b) => {
              const key = b.leaveType;
              const editingVal = draft[key];
              return (
                <tr key={b.id}>
                  <td className="py-1.5 font-medium text-slate-700">{LEAVE_LABELS[b.leaveType] ?? b.leaveType}</td>
                  <td className="py-1.5 text-right">
                    {canEdit ? (
                      <input type="number" min="0" step="0.5"
                        className="w-16 rounded border border-slate-200 px-2 py-1 text-right text-sm"
                        value={editingVal ?? String(b.entitledDays)}
                        onChange={(e) => setDraft((d) => ({ ...d, [key]: e.target.value }))} />
                    ) : b.entitledDays}
                  </td>
                  <td className="py-1.5 text-right text-slate-500">{b.consumedDays}</td>
                  <td className="py-1.5 text-right font-semibold tabular-nums">{b.remainingDays}</td>
                  {canEdit && (
                    <td className="py-1.5 text-right">
                      {editingVal !== undefined && Number(editingVal) !== b.entitledDays && (
                        <Button size="sm" variant="secondary" disabled={save.isPending}
                          onClick={() => save.mutate({ leaveType: b.leaveType, entitledDays: Number(editingVal) })}>
                          <Save size={12} className="mr-1" />Save
                        </Button>
                      )}
                    </td>
                  )}
                </tr>
              );
            })}
            {balancesQ.data.length === 0 && (
              <tr><td colSpan={canEdit ? 5 : 4} className="py-3 text-center text-slate-400 text-xs">No leave balances yet.</td></tr>
            )}
          </tbody>
        </table>
      )}
    </section>
  );
}

// ─── Invite teacher modal ─────────────────────────────────────────────────
function InviteTeacherModal({
  open, tenantId, onClose, onCreated,
}: {
  open: boolean;
  tenantId: string;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [form, setForm] = useState<InviteTeacherRequest>({
    firstName: '',
    email: '',
    role: 'CLASS_TEACHER',
  });
  const [success, setSuccess] = useState(false);

  // Sections (flattened) for the optional one-step class-teacher assignment.
  const classesQ = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: open,
    staleTime: 5 * 60_000,
  });
  const sectionOptions = (classesQ.data ?? []).flatMap((c) =>
    c.sections.map((s) => ({ id: s.id, label: `${c.name} - ${s.name}` })));

  const p: StaffProfile = form.profile ?? {};
  const setP = (patch: Partial<StaffProfile>) =>
    setForm((f) => ({ ...f, profile: { ...(f.profile ?? {}), ...patch } }));

  const invite = useMutation({
    mutationFn: () => schoolApi.inviteTeacher(tenantId, form),
    onSuccess: () => {
      setSuccess(true);
      setTimeout(() => {
        setSuccess(false);
        setForm({ firstName: '', email: '', role: 'CLASS_TEACHER' });
        onCreated();
      }, 2000);
    },
  });

  function handleClose() {
    invite.reset();
    setSuccess(false);
    setForm({ firstName: '', email: '', role: 'CLASS_TEACHER' });
    onClose();
  }

  return (
    <Modal open={open} onClose={handleClose} title="Invite teacher">
      {success ? (
        <div className="text-center py-6 space-y-2">
          <MailCheck size={36} className="mx-auto text-green-500" />
          <p className="font-medium text-slate-800">Invite sent!</p>
          <p className="text-sm text-slate-500">
            {form.firstName} will receive login details at <strong>{form.email}</strong>.
          </p>
        </div>
      ) : (
        <form
          onSubmit={(e) => { e.preventDefault(); invite.mutate(); }}
          className="space-y-3"
        >
          {invite.isError && <ErrorBanner error={invite.error} />}

          <div className="grid grid-cols-2 gap-3">
            <Input
              label="First name"
              value={form.firstName}
              required
              onChange={(e) => setForm({ ...form, firstName: e.target.value })}
            />
            <Input
              label="Last name"
              value={form.lastName ?? ''}
              onChange={(e) => setForm({ ...form, lastName: e.target.value })}
            />
          </div>

          <Input
            label="Email address"
            type="email"
            value={form.email}
            required
            placeholder="teacher@school.in"
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />

          <Input
            label="Phone (optional)"
            type="tel"
            value={form.phone ?? ''}
            placeholder="9876543210"
            onChange={(e) => setForm({ ...form, phone: e.target.value })}
          />

          <label className="block">
            <span className="text-sm text-slate-700 mb-1 inline-block">Role</span>
            <select
              value={form.role}
              onChange={(e) => setForm({ ...form, role: e.target.value as InviteTeacherRequest['role'] })}
              className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
            >
              {TEACHER_ROLES.map((r) => (
                <option key={r.value} value={r.value}>{r.label}</option>
              ))}
            </select>
          </label>

          <div className="grid grid-cols-2 gap-3">
            <label className="block">
              <span className="text-sm text-slate-700 mb-1 inline-block">Gender (optional)</span>
              <select
                value={form.gender ?? ''}
                onChange={(e) => setForm({ ...form, gender: e.target.value })}
                className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">— select —</option>
                {GENDERS.map((g) => (
                  <option key={g} value={g}>{g.charAt(0) + g.slice(1).toLowerCase()}</option>
                ))}
              </select>
            </label>
            <Input
              label="Date of joining (optional)"
              type="date"
              value={form.dateOfJoining ?? ''}
              onChange={(e) => setForm({ ...form, dateOfJoining: e.target.value })}
            />
          </div>

          {form.role === 'CLASS_TEACHER' && sectionOptions.length > 0 && (
            <label className="block">
              <span className="text-sm text-slate-700 mb-1 inline-block">Class teacher of (optional)</span>
              <select
                value={form.classTeacherSectionId ?? ''}
                onChange={(e) => setForm({ ...form, classTeacherSectionId: e.target.value || undefined })}
                className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
              >
                <option value="">— not assigned —</option>
                {sectionOptions.map((s) => (
                  <option key={s.id} value={s.id}>{s.label}</option>
                ))}
              </select>
            </label>
          )}

          {/* ── Professional details ── */}
          <SectionHeading>Professional details</SectionHeading>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Qualification" placeholder="B.Ed, M.Sc" value={p.qualification ?? ''}
              onChange={(e) => setP({ qualification: e.target.value })} />
            <Input label="Designation" placeholder="Senior Teacher" value={p.designation ?? ''}
              onChange={(e) => setP({ designation: e.target.value })} />
            <Input label="Experience (years)" type="number" value={p.experienceYears ?? ''}
              onChange={(e) => setP({ experienceYears: e.target.value ? Number(e.target.value) : null })} />
            <Input label="Employee code" value={p.employeeCode ?? ''}
              onChange={(e) => setP({ employeeCode: e.target.value })} />
            <label className="block col-span-2">
              <span className="text-sm text-slate-700 mb-1 inline-block">Employment type</span>
              <select value={p.employmentType ?? ''} onChange={(e) => setP({ employmentType: e.target.value })}
                className="block w-full rounded border border-slate-300 px-3 py-2 text-sm">
                <option value="">— select —</option>
                {['FULL_TIME', 'PART_TIME', 'CONTRACT', 'VISITING'].map((t) => (
                  <option key={t} value={t}>{t.replace('_', ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())}</option>
                ))}
              </select>
            </label>
          </div>

          {/* ── Identity (KYC) ── */}
          <SectionHeading>Identity (KYC)</SectionHeading>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Aadhaar number" placeholder="XXXX XXXX XXXX" value={p.aadhaarNumber ?? ''}
              onChange={(e) => setP({ aadhaarNumber: e.target.value })} />
            <Input label="PAN number" placeholder="ABCDE1234F" value={p.panNumber ?? ''}
              onChange={(e) => setP({ panNumber: e.target.value })} />
          </div>

          {/* ── Bank / account ── */}
          <SectionHeading>Bank account (for payroll)</SectionHeading>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Bank name" value={p.bankName ?? ''}
              onChange={(e) => setP({ bankName: e.target.value })} />
            <Input label="Account number" value={p.bankAccountNumber ?? ''}
              onChange={(e) => setP({ bankAccountNumber: e.target.value })} />
            <Input label="IFSC code" placeholder="HDFC0001234" value={p.ifscCode ?? ''}
              onChange={(e) => setP({ ifscCode: e.target.value })} />
          </div>

          {/* ── Personal ── */}
          <SectionHeading>Personal</SectionHeading>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Date of birth" type="date" value={p.dateOfBirth ?? ''}
              onChange={(e) => setP({ dateOfBirth: e.target.value })} />
            <Input label="Emergency contact" type="tel" value={p.emergencyContact ?? ''}
              onChange={(e) => setP({ emergencyContact: e.target.value })} />
            <div className="col-span-2">
              <label className="block text-sm text-slate-700 mb-1">Address</label>
              <textarea className="w-full rounded border border-slate-300 px-3 py-2 text-sm" rows={2}
                value={p.address ?? ''} onChange={(e) => setP({ address: e.target.value })} />
            </div>
          </div>

          <p className="text-xs text-slate-500 bg-slate-50 rounded px-3 py-2">
            Identity & bank numbers are stored securely and shown masked (last 4 digits) afterwards.
            A temporary password is emailed to the teacher; they must change it on first login.
          </p>

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" type="button" onClick={handleClose} disabled={invite.isPending}>
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={invite.isPending || !form.firstName || !form.email}
            >
              {invite.isPending ? 'Sending invite…' : 'Send invite'}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}
