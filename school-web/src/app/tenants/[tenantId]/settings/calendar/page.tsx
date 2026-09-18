'use client';

/**
 * School Calendar settings — per-tenant working days + holidays.
 *
 * Admins choose which weekdays the school operates and add holidays/events. This feeds the
 * timetable workload calculation and is the source of truth for "is the school open today?".
 */

import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { CalendarDays, Plus, Trash2 } from 'lucide-react';
import { calendarApi, type Holiday } from '@/api/endpoints/calendar';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';

const DAYS = [
  { n: 1, label: 'Mon' }, { n: 2, label: 'Tue' }, { n: 3, label: 'Wed' }, { n: 4, label: 'Thu' },
  { n: 5, label: 'Fri' }, { n: 6, label: 'Sat' }, { n: 7, label: 'Sun' },
];
const TYPES = ['HOLIDAY', 'EVENT', 'EXAM', 'VACATION'];
const TYPE_TONE: Record<string, 'danger' | 'info' | 'warning' | 'primary'> = {
  HOLIDAY: 'danger', EVENT: 'info', EXAM: 'warning', VACATION: 'primary',
};

export default function CalendarSettingsPage() {
  return (
    <RequireRole roles={OWNER_OR_ADMIN}>
      <CalendarSettings />
    </RequireRole>
  );
}

function CalendarSettings() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();

  const q = useQuery({
    queryKey: ['calendar', tenantId],
    queryFn: () => calendarApi.get(tenantId),
    enabled: !!tenantId,
  });

  const [days, setDays] = useState<number[]>([]);
  const [form, setForm] = useState({ date: '', name: '', type: 'HOLIDAY' });

  useEffect(() => {
    if (q.data) setDays(q.data.workingDays);
  }, [q.data]);

  const invalidate = () => qc.invalidateQueries({ queryKey: ['calendar', tenantId] });

  const saveDays = useMutation({
    mutationFn: () => calendarApi.setWorkingDays(tenantId, days),
    onSuccess: () => { invalidate(); toast.success('Working days saved'); },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Failed to save working days'),
  });

  const addHoliday = useMutation({
    mutationFn: () => calendarApi.addHoliday(tenantId, form),
    onSuccess: () => { invalidate(); setForm({ date: '', name: '', type: 'HOLIDAY' }); toast.success('Holiday added'); },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Failed to add holiday'),
  });

  const delHoliday = useMutation({
    mutationFn: (id: string) => calendarApi.deleteHoliday(tenantId, id),
    onSuccess: () => { invalidate(); toast.success('Holiday removed'); },
    onError: () => toast.error('Failed to remove holiday'),
  });

  if (q.isLoading) return <div className="flex items-center gap-2 text-slate-500"><Spinner /> Loading calendar…</div>;
  if (q.isError) return <ErrorBanner error={q.error} onRetry={() => q.refetch()} />;

  const toggle = (n: number) => setDays((d) => (d.includes(n) ? d.filter((x) => x !== n) : [...d, n].sort()));
  const dirty = q.data ? JSON.stringify([...days].sort()) !== JSON.stringify([...q.data.workingDays].sort()) : false;

  return (
    <div className="max-w-3xl space-y-6">
      <PageHeader title="School Calendar" description="Set your working days and holidays. Used across attendance, timetable and reports." icon={<CalendarDays />} />

      {/* Working days */}
      <Card>
        <CardHeader><CardTitle className="text-base">Working Days</CardTitle></CardHeader>
        <CardBody className="space-y-4">
          <div className="flex flex-wrap gap-2">
            {DAYS.map((d) => {
              const on = days.includes(d.n);
              return (
                <button
                  key={d.n}
                  type="button"
                  onClick={() => toggle(d.n)}
                  className={`h-11 w-14 rounded-lg border text-sm font-medium transition-colors ${
                    on ? 'border-primary bg-primary text-white' : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300'
                  }`}
                >
                  {d.label}
                </button>
              );
            })}
          </div>
          <div className="flex items-center gap-3">
            <Button onClick={() => saveDays.mutate()} disabled={!dirty || saveDays.isPending}>
              {saveDays.isPending ? 'Saving…' : 'Save working days'}
            </Button>
            <span className="text-xs text-slate-400">{days.length} working day{days.length !== 1 ? 's' : ''} / week</span>
          </div>
        </CardBody>
      </Card>

      {/* Holidays */}
      <Card>
        <CardHeader><CardTitle className="text-base">Holidays & Events</CardTitle></CardHeader>
        <CardBody className="space-y-4">
          {/* Add form */}
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-[auto_1fr_auto_auto] sm:items-end">
            <div>
              <label className="mb-1 block text-xs text-slate-500">Date</label>
              <Input type="date" value={form.date} onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))} />
            </div>
            <div>
              <label className="mb-1 block text-xs text-slate-500">Name</label>
              <Input placeholder="e.g. Diwali, Sports Day" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
            </div>
            <div>
              <label className="mb-1 block text-xs text-slate-500">Type</label>
              <select
                className="h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm"
                value={form.type}
                onChange={(e) => setForm((f) => ({ ...f, type: e.target.value }))}
              >
                {TYPES.map((t) => <option key={t} value={t}>{t.charAt(0) + t.slice(1).toLowerCase()}</option>)}
              </select>
            </div>
            <Button onClick={() => addHoliday.mutate()} disabled={!form.date || !form.name.trim() || addHoliday.isPending}>
              <Plus size={15} className="mr-1" />Add
            </Button>
          </div>

          {/* List */}
          {q.data && q.data.holidays.length === 0 ? (
            <p className="py-6 text-center text-sm text-slate-400">No holidays added yet.</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-slate-400">
                  <th className="px-2 py-2 font-medium">Date</th>
                  <th className="px-2 py-2 font-medium">Name</th>
                  <th className="px-2 py-2 font-medium">Type</th>
                  <th className="px-2 py-2" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {q.data?.holidays.map((h: Holiday) => (
                  <tr key={h.id} className="hover:bg-slate-50/60">
                    <td className="px-2 py-2 font-medium">{h.date}</td>
                    <td className="px-2 py-2">{h.name}</td>
                    <td className="px-2 py-2"><Badge tone={TYPE_TONE[h.type] ?? 'neutral'}>{h.type}</Badge></td>
                    <td className="px-2 py-2 text-right">
                      <button
                        className="text-slate-400 hover:text-red-500"
                        onClick={() => delHoliday.mutate(h.id)}
                        disabled={delHoliday.isPending}
                        aria-label="Remove holiday"
                      >
                        <Trash2 size={15} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
