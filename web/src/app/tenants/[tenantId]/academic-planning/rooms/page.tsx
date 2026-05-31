'use client';

/**
 * Rooms / Classrooms management. Admins define the school's physical rooms; the timetable then
 * schedules slots into them and rejects double-bookings (same room, same period + day).
 */

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { DoorOpen, Plus, Trash2 } from 'lucide-react';
import { roomsApi, type Classroom, type SaveRoom } from '@/api/endpoints/rooms';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { Spinner } from '@/components/ui/Spinner';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';

const ROOM_TYPES = ['CLASSROOM', 'LAB', 'LIBRARY', 'HALL', 'OTHER'];
const BLANK: SaveRoom = { name: '', code: '', building: '', capacity: null, roomType: 'CLASSROOM' };

export default function RoomsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState<SaveRoom>(BLANK);

  const q = useQuery({
    queryKey: ['classrooms', tenantId],
    queryFn: () => roomsApi.list(tenantId),
    enabled: !!tenantId,
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ['classrooms', tenantId] });

  const add = useMutation({
    mutationFn: () => roomsApi.create(tenantId, {
      ...form,
      capacity: form.capacity ? Number(form.capacity) : null,
    }),
    onSuccess: () => { invalidate(); setForm(BLANK); toast.success('Room added'); },
    onError: (e: unknown) => toast.error(e instanceof Error ? e.message : 'Failed to add room'),
  });

  const del = useMutation({
    mutationFn: (id: string) => roomsApi.remove(tenantId, id),
    onSuccess: () => { invalidate(); toast.success('Room removed'); },
    onError: () => toast.error('Failed to remove room'),
  });

  if (q.isLoading) return <div className="flex items-center gap-2 text-slate-500"><Spinner /> Loading rooms…</div>;
  if (q.isError) return <ErrorBanner error={q.error} onRetry={() => q.refetch()} />;

  return (
    <div className="max-w-4xl space-y-6">
      <PageHeader title="Rooms & Classrooms" description="Define rooms so the timetable can schedule classes into them and prevent double-booking." icon={<DoorOpen />} />

      <Card>
        <CardHeader><CardTitle className="text-base">Add a room</CardTitle></CardHeader>
        <CardBody>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-[1.5fr_1fr_1fr_0.8fr_1fr_auto] sm:items-end">
            <Field label="Name"><Input placeholder="e.g. Room 8A" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} /></Field>
            <Field label="Code"><Input placeholder="R-8A" value={form.code ?? ''} onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))} /></Field>
            <Field label="Building"><Input placeholder="Main" value={form.building ?? ''} onChange={(e) => setForm((f) => ({ ...f, building: e.target.value }))} /></Field>
            <Field label="Capacity"><Input type="number" value={form.capacity ?? ''} onChange={(e) => setForm((f) => ({ ...f, capacity: e.target.value ? Number(e.target.value) : null }))} /></Field>
            <Field label="Type">
              <select className="h-10 w-full rounded-lg border border-slate-200 bg-white px-2 text-sm"
                value={form.roomType} onChange={(e) => setForm((f) => ({ ...f, roomType: e.target.value }))}>
                {ROOM_TYPES.map((t) => <option key={t} value={t}>{t.charAt(0) + t.slice(1).toLowerCase()}</option>)}
              </select>
            </Field>
            <Button onClick={() => add.mutate()} disabled={!form.name.trim() || add.isPending}>
              <Plus size={15} className="mr-1" />Add
            </Button>
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHeader><CardTitle className="text-base">Rooms ({q.data?.length ?? 0})</CardTitle></CardHeader>
        <CardBody className="p-0 overflow-x-auto">
          {q.data && q.data.length === 0 ? (
            <p className="py-8 text-center text-sm text-slate-400">No rooms yet. Add your first room above.</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="border-b border-slate-100">
                <tr className="text-left text-xs uppercase tracking-wide text-slate-400">
                  <th className="px-4 py-3 font-medium">Name</th>
                  <th className="px-4 py-3 font-medium">Code</th>
                  <th className="px-4 py-3 font-medium">Building</th>
                  <th className="px-4 py-3 font-medium">Capacity</th>
                  <th className="px-4 py-3 font-medium">Type</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {q.data?.map((r: Classroom) => (
                  <tr key={r.id} className="hover:bg-slate-50/60">
                    <td className="px-4 py-3 font-medium">{r.name}</td>
                    <td className="px-4 py-3 text-slate-500">{r.code || '—'}</td>
                    <td className="px-4 py-3 text-slate-500">{r.building || '—'}</td>
                    <td className="px-4 py-3 text-slate-500">{r.capacity ?? '—'}</td>
                    <td className="px-4 py-3"><Badge tone="neutral">{r.roomType}</Badge></td>
                    <td className="px-4 py-3 text-right">
                      <button className="text-slate-400 hover:text-red-500" aria-label="Remove room"
                        onClick={() => del.mutate(r.id)} disabled={del.isPending}>
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

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="mb-1 block text-xs text-slate-500">{label}</label>
      {children}
    </div>
  );
}
