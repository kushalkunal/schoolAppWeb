'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { ptmApi } from '@/api/endpoints/dailyOps';
import { schoolApi } from '@/api/endpoints/school';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Spinner } from '@/components/ui/Spinner';
import { EmptyState } from '@/components/ui/EmptyState';
import { StudentPicker } from '@/components/pickers/StudentPicker';
import { StaffPicker } from '@/components/pickers/StaffPicker';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { isFeatureEnabled } from '@/features/featureFlags';

export default function PtmPage() {
  if (!isFeatureEnabled('PTM_SCHEDULING')) return <EmptyState title="PTM scheduling is disabled" />;
  return <RequireRole roles={OWNER_OR_ADMIN}><Inner /></RequireRole>;
}

function Inner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [openSlot, setOpenSlot] = useState(false);
  const [bookingFor, setBookingFor] = useState<string | null>(null);

  const slotsQ = useQuery({
    queryKey: ['ptm-slots', tenantId, date],
    queryFn: () => ptmApi.slots(tenantId, date),
  });
  const staffQ = useQuery({
    queryKey: ['staff-list-for-picker', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    staleTime: 60_000,
  });
  const teacherNameOf = (id: string) =>
    staffQ.data?.find((s) => s.id === id)?.displayName ?? id.slice(0, 8) + '…';

  return (
    <div className="space-y-4">
      <PageHeader title="Parent-teacher meetings" description="Slot-based PTM booking. Parent gets a confirmation WA + email."
        actions={<Button onClick={() => setOpenSlot(true)}><Plus size={14} className="mr-1" /> Create slot</Button>} />
      <Card>
        <CardBody className="space-y-3">
          <div className="flex items-center gap-2">
            <label className="text-sm">Date:</label>
            <input type="date" className="border border-slate-200 rounded px-2 py-1 text-sm"
              value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          {slotsQ.isLoading ? <Spinner /> : (slotsQ.data ?? []).length === 0 ? (
            <p className="text-sm text-slate-500">No slots scheduled for this date.</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="text-xs text-slate-500 text-left">
                <tr><th>Time</th><th>Teacher</th><th>Capacity</th><th>Booked</th><th></th></tr>
              </thead>
              <tbody>
                {(slotsQ.data ?? []).map(s => (
                  <tr key={s.id} className="border-t border-slate-100">
                    <td className="py-1.5">{s.startTime} – {s.endTime}</td>
                    <td className="font-medium">{teacherNameOf(s.teacherId)}</td>
                    <td>{s.capacity}</td>
                    <td>{s.bookedCount}/{s.capacity}</td>
                    <td className="text-right">
                      <Button variant="ghost" disabled={s.bookedCount >= s.capacity}
                        onClick={() => setBookingFor(s.id)}>Book</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </CardBody>
      </Card>
      {openSlot && <SlotForm tenantId={tenantId} date={date} onClose={() => setOpenSlot(false)} onSaved={() => {
        setOpenSlot(false); qc.invalidateQueries({ queryKey: ['ptm-slots', tenantId, date] });
      }} />}
      {bookingFor && <BookForm tenantId={tenantId} slotId={bookingFor} onClose={() => setBookingFor(null)} onSaved={() => {
        setBookingFor(null); qc.invalidateQueries({ queryKey: ['ptm-slots', tenantId, date] });
      }} />}
    </div>
  );
}

function SlotForm({ tenantId, date, onClose, onSaved }: { tenantId: string; date: string; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({ teacherId: '', startTime: '15:00', endTime: '15:15', capacity: 1 });
  const mut = useMutation({
    mutationFn: () => ptmApi.createSlot(tenantId, { ...form, date }),
    onSuccess: onSaved,
  });
  return (
    <Modal open onClose={onClose} title={`Create slot for ${date}`}>
      <div className="space-y-3">
        <StaffPicker
          tenantId={tenantId}
          value={form.teacherId || null}
          onChange={(id) => setForm({ ...form, teacherId: id ?? '' })}
          label="Teacher"
          required
          rolesFilter={['PRINCIPAL', 'CLASS_TEACHER', 'SUBJECT_TEACHER', 'ADMIN']}
        />
        <div className="grid grid-cols-2 gap-3">
          <Input label="Start time" type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} />
          <Input label="End time" type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} />
        </div>
        <Input label="Capacity" type="number" min={1} value={form.capacity} onChange={(e) => setForm({ ...form, capacity: Number(e.target.value) })} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={!form.teacherId || mut.isPending} onClick={() => mut.mutate()}>
            {mut.isPending ? 'Saving…' : 'Create'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

function BookForm({ tenantId, slotId, onClose, onSaved }: { tenantId: string; slotId: string; onClose: () => void; onSaved: () => void }) {
  const [studentId, setStudentId] = useState('');
  const [notes, setNotes] = useState('');
  const mut = useMutation({
    mutationFn: () => ptmApi.book(tenantId, slotId, studentId, notes || undefined),
    onSuccess: onSaved,
  });
  return (
    <Modal open onClose={onClose} title="Book slot">
      <div className="space-y-3">
        <StudentPicker
          tenantId={tenantId}
          value={studentId || null}
          onChange={(id) => setStudentId(id ?? '')}
          required
        />
        <Input label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={!studentId || mut.isPending} onClick={() => mut.mutate()}>
            {mut.isPending ? 'Booking…' : 'Book + notify parent'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
