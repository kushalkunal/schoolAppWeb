'use client';

import { useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Building2, Plus, DoorOpen, UserCheck, LogIn, LogOut } from 'lucide-react';
import { hostelApi, type Hostel, type HostelRoom } from '@/api/endpoints/hostel';
import { studentsApi } from '@/api/endpoints/students';
import { StudentPicker } from '@/components/pickers/StudentPicker';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Badge } from '@/components/ui/Badge';
import { Stat } from '@/components/ui/Stat';
import { EmptyState } from '@/components/ui/EmptyState';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { useToast } from '@/components/ui/Toast';
import { hasCode, isApiError } from '@/api/errors';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { cn } from '@/lib/utils';

export default function HostelPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();

  const [selectedHostelId, setSelectedHostelId] = useState<string | null>(null);
  const [hostelOpen, setHostelOpen] = useState(false);
  const [roomOpen, setRoomOpen] = useState(false);
  const [allocateRoom, setAllocateRoom] = useState<HostelRoom | null>(null);
  const [visitorOpen, setVisitorOpen] = useState(false);

  const hostelsQ = useQuery({
    queryKey: ['hostels', tenantId],
    queryFn: () => hostelApi.list(tenantId),
    enabled: !!tenantId,
    retry: false,
  });

  const activeHostelId = selectedHostelId ?? hostelsQ.data?.[0]?.id ?? null;

  const roomsQ = useQuery({
    queryKey: ['hostel-rooms', tenantId, activeHostelId],
    queryFn: () => hostelApi.rooms(tenantId, activeHostelId!),
    enabled: !!tenantId && !!activeHostelId,
  });
  const visitorsQ = useQuery({
    queryKey: ['hostel-visitors', tenantId],
    queryFn: () => hostelApi.inside(tenantId),
    enabled: !!tenantId,
  });

  const totals = useMemo(() => {
    const rooms = roomsQ.data ?? [];
    return {
      rooms: rooms.length,
      capacity: rooms.reduce((a, r) => a + r.capacity, 0),
      occupied: rooms.reduce((a, r) => a + r.currentOccupancy, 0),
      vacant: rooms.reduce((a, r) => a + Math.max(0, r.capacity - r.currentOccupancy), 0),
    };
  }, [roomsQ.data]);

  if (hostelsQ.isError && hasCode(hostelsQ.error, 'FEATURE_DISABLED')) {
    return (
      <div className="space-y-6">
        <PageHeader title="Hostel" icon={<Building2 size={18} />} />
        <EmptyState
          icon={<Building2 size={28} />}
          title="Hostel isn't enabled for your plan"
          description="Manage hostel buildings, rooms, allocations and visitor logs."
        />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <PageHeader
        title="Hostel"
        description="Buildings, rooms, allocations, visitor logs"
        icon={<Building2 size={18} />}
        actions={
          <RequireRole roles={OWNER_OR_ADMIN}>
            <Button variant="secondary" size="sm" onClick={() => setVisitorOpen(true)}>
              <LogIn size={14} /> Sign in visitor
            </Button>
            <Button size="sm" onClick={() => setHostelOpen(true)}><Plus size={14} /> Add hostel</Button>
          </RequireRole>
        }
      />

      {hostelsQ.isLoading && <Skeleton className="h-32" />}

      {hostelsQ.data && hostelsQ.data.length === 0 && (
        <EmptyState
          icon={<Building2 size={28} />}
          title="No hostels yet"
          description="Add a hostel building to start managing rooms and allocations."
          action={
            <RequireRole roles={OWNER_OR_ADMIN}>
              <Button onClick={() => setHostelOpen(true)}><Plus size={14} /> Add hostel</Button>
            </RequireRole>
          }
        />
      )}

      {hostelsQ.data && hostelsQ.data.length > 0 && (
        <>
          {/* Hostel selector */}
          <div className="flex flex-wrap gap-2">
            {hostelsQ.data.map((h) => (
              <button
                key={h.id}
                onClick={() => setSelectedHostelId(h.id)}
                className={cn(
                  'inline-flex items-center gap-2 px-3 py-2 rounded-brand text-sm font-medium transition',
                  activeHostelId === h.id
                    ? 'bg-primary text-primary-fg shadow-sm'
                    : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50',
                )}
              >
                <Building2 size={14} /> {h.name}
                {h.gender && <Badge tone="neutral" size="sm">{h.gender}</Badge>}
              </button>
            ))}
          </div>

          {/* KPI tiles */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <Stat label="Rooms" value={totals.rooms} icon={<DoorOpen size={16} />} tone="primary" />
            <Stat label="Capacity" value={totals.capacity} icon={<UserCheck size={16} />} tone="info" />
            <Stat label="Occupied" value={totals.occupied} icon={<UserCheck size={16} />} tone="accent" />
            <Stat label="Vacant" value={totals.vacant} icon={<DoorOpen size={16} />}
                  tone={totals.vacant > 0 ? 'success' : 'warning'} />
          </div>

          {/* Rooms */}
          <Card padding="none" className="overflow-hidden">
            <CardHeader className="flex items-center justify-between">
              <div>
                <CardTitle>Rooms</CardTitle>
                <CardDescription>Click an available room to allocate a student</CardDescription>
              </div>
              <RequireRole roles={OWNER_OR_ADMIN}>
                <Button size="sm" onClick={() => setRoomOpen(true)}><Plus size={14} /> Add room</Button>
              </RequireRole>
            </CardHeader>
            {roomsQ.isLoading && <CardBody><Skeleton className="h-32" /></CardBody>}
            {roomsQ.data && roomsQ.data.length === 0 && (
              <CardBody><p className="text-sm text-slate-500 py-6 text-center">No rooms in this hostel yet.</p></CardBody>
            )}
            {roomsQ.data && roomsQ.data.length > 0 && (
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-2 p-3">
                {roomsQ.data.map((r) => {
                  const full = r.currentOccupancy >= r.capacity;
                  return (
                    <button
                      key={r.id}
                      onClick={() => !full && setAllocateRoom(r)}
                      disabled={full}
                      className={cn(
                        'rounded-brand p-3 text-left border transition',
                        full
                          ? 'bg-slate-100 border-slate-200 text-slate-500 cursor-not-allowed'
                          : 'bg-success/5 border-success/30 text-slate-800 hover:border-primary hover:shadow-sm',
                      )}
                    >
                      <div className="text-sm font-bold">{r.roomNumber}</div>
                      <div className="text-[10px] text-slate-500 uppercase">{r.roomType}</div>
                      <div className="text-xs mt-1">{r.currentOccupancy}/{r.capacity}</div>
                    </button>
                  );
                })}
              </div>
            )}
          </Card>

          {/* Active visitors */}
          {visitorsQ.data && visitorsQ.data.length > 0 && (
            <Card padding="none" className="overflow-hidden">
              <CardHeader>
                <CardTitle>Currently inside ({visitorsQ.data.length})</CardTitle>
                <CardDescription>Visitors who haven't signed out yet</CardDescription>
              </CardHeader>
              <ul className="divide-y divide-slate-100">
                {visitorsQ.data.map((v) => (
                  <li key={v.id} className="px-5 py-3 flex items-center justify-between">
                    <div className="min-w-0">
                      <div className="text-sm font-semibold text-slate-900">{v.visitorName}</div>
                      <div className="text-xs text-slate-500">
                        {v.relation && `${v.relation} · `}
                        {v.visitorPhone && `${v.visitorPhone} · `}
                        In since {new Date(v.inTime).toLocaleString('en-IN')}
                      </div>
                    </div>
                    <RequireRole roles={OWNER_OR_ADMIN}>
                      <Button size="sm" variant="secondary"
                              onClick={async () => {
                                try {
                                  await hostelApi.signOut(tenantId, v.id);
                                  toast.success('Visitor signed out');
                                  qc.invalidateQueries({ queryKey: ['hostel-visitors', tenantId] });
                                } catch (e) {
                                  toast.error(isApiError(e) ? e.message : 'Sign-out failed');
                                }
                              }}>
                        <LogOut size={14} /> Sign out
                      </Button>
                    </RequireRole>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </>
      )}

      <CreateHostelModal open={hostelOpen} onClose={() => setHostelOpen(false)} tenantId={tenantId} />
      <CreateRoomModal open={roomOpen} onClose={() => setRoomOpen(false)}
                       tenantId={tenantId} hostelId={activeHostelId} />
      {allocateRoom && (
        <AllocateModal room={allocateRoom} tenantId={tenantId}
                       onClose={() => setAllocateRoom(null)} />
      )}
      <SignInVisitorModal open={visitorOpen} onClose={() => setVisitorOpen(false)}
                          tenantId={tenantId} hostels={hostelsQ.data ?? []} />
    </div>
  );
}

// ---------- Modals ----------

function CreateHostelModal({ open, onClose, tenantId }:
  { open: boolean; onClose: () => void; tenantId: string }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({ name: '', gender: '', address: '' });
  const create = useMutation({
    mutationFn: () => hostelApi.create(tenantId, form),
    onSuccess: () => {
      toast.success('Hostel added');
      qc.invalidateQueries({ queryKey: ['hostels', tenantId] });
      onClose();
      setForm({ name: '', gender: '', address: '' });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Failed'),
  });
  return (
    <Modal open={open} onClose={onClose} title="Add hostel">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        <Input label="Name" required value={form.name}
               onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Gender</span>
          <select value={form.gender}
            onChange={(e) => setForm({ ...form, gender: e.target.value })}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          >
            <option value="">— Any —</option>
            <option value="BOYS">Boys</option>
            <option value="GIRLS">Girls</option>
            <option value="COED">Co-ed</option>
          </select>
        </label>
        <Input label="Address" value={form.address}
               onChange={(e) => setForm({ ...form, address: e.target.value })} />
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={create.isPending} disabled={!form.name}>Add hostel</Button>
        </div>
      </form>
    </Modal>
  );
}

function CreateRoomModal({ open, onClose, tenantId, hostelId }:
  { open: boolean; onClose: () => void; tenantId: string; hostelId: string | null }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({ roomNumber: '', floor: 1, roomType: 'DOUBLE', capacity: 2 });
  const create = useMutation({
    mutationFn: () => hostelApi.createRoom(tenantId, hostelId!, form),
    onSuccess: () => {
      toast.success('Room added');
      qc.invalidateQueries({ queryKey: ['hostel-rooms', tenantId, hostelId] });
      onClose();
      setForm({ roomNumber: '', floor: 1, roomType: 'DOUBLE', capacity: 2 });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Failed'),
  });
  return (
    <Modal open={open} onClose={onClose} title="Add room">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        <Input label="Room number" required value={form.roomNumber}
               onChange={(e) => setForm({ ...form, roomNumber: e.target.value })} />
        <div className="grid grid-cols-3 gap-3">
          <Input label="Floor" type="number" value={form.floor}
                 onChange={(e) => setForm({ ...form, floor: Number(e.target.value) })} />
          <label className="block">
            <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Type</span>
            <select value={form.roomType}
              onChange={(e) => setForm({ ...form, roomType: e.target.value })}
              className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
            >
              {['SINGLE', 'DOUBLE', 'TRIPLE', 'DORM'].map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </label>
          <Input label="Capacity" type="number" min={1} required value={form.capacity}
                 onChange={(e) => setForm({ ...form, capacity: Number(e.target.value) })} />
        </div>
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={create.isPending} disabled={!form.roomNumber || !hostelId}>
            Add room
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function AllocateModal({ room, tenantId, onClose }:
  { room: HostelRoom; tenantId: string; onClose: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [studentId, setStudentId] = useState<string | null>(null);
  const allocate = useMutation({
    mutationFn: () => hostelApi.allocate(tenantId, room.id, studentId!),
    onSuccess: () => {
      toast.success('Allocated');
      qc.invalidateQueries({ queryKey: ['hostel-rooms', tenantId] });
      onClose();
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Failed'),
  });
  return (
    <Modal open={true} onClose={onClose} title={`Allocate room ${room.roomNumber}`}>
      <form onSubmit={(e) => { e.preventDefault(); allocate.mutate(); }} className="space-y-3">
        <p className="text-sm text-slate-600">
          Room type: <strong>{room.roomType}</strong> · Occupancy {room.currentOccupancy}/{room.capacity}
        </p>
        <StudentPicker tenantId={tenantId} value={studentId} onChange={setStudentId} required />
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={allocate.isPending} disabled={!studentId}>Allocate</Button>
        </div>
      </form>
    </Modal>
  );
}

function SignInVisitorModal({ open, onClose, tenantId, hostels }:
  { open: boolean; onClose: () => void; tenantId: string; hostels: Hostel[] }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({
    hostelId: '', visitorName: '', visitorPhone: '', relation: '',
    idProof: '', purpose: '',
  });
  const signIn = useMutation({
    mutationFn: () => hostelApi.signIn(tenantId, form),
    onSuccess: () => {
      toast.success('Visitor signed in');
      qc.invalidateQueries({ queryKey: ['hostel-visitors', tenantId] });
      onClose();
      setForm({ hostelId: '', visitorName: '', visitorPhone: '', relation: '', idProof: '', purpose: '' });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Failed'),
  });
  return (
    <Modal open={open} onClose={onClose} title="Sign in visitor">
      <form onSubmit={(e) => { e.preventDefault(); signIn.mutate(); }} className="space-y-3">
        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Hostel</span>
          <select required value={form.hostelId}
            onChange={(e) => setForm({ ...form, hostelId: e.target.value })}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          >
            <option value="">Select hostel</option>
            {hostels.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
          </select>
        </label>
        <Input label="Visitor name" required value={form.visitorName}
               onChange={(e) => setForm({ ...form, visitorName: e.target.value })} />
        <div className="grid grid-cols-2 gap-3">
          <Input label="Phone" type="tel" value={form.visitorPhone}
                 onChange={(e) => setForm({ ...form, visitorPhone: e.target.value })} />
          <Input label="Relation" value={form.relation}
                 onChange={(e) => setForm({ ...form, relation: e.target.value })} />
        </div>
        <Input label="ID proof" value={form.idProof}
               onChange={(e) => setForm({ ...form, idProof: e.target.value })} />
        <Input label="Purpose" value={form.purpose}
               onChange={(e) => setForm({ ...form, purpose: e.target.value })} />
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={signIn.isPending}
                  disabled={!form.hostelId || !form.visitorName}>
            Sign in
          </Button>
        </div>
      </form>
    </Modal>
  );
}
