'use client';

import { useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Bus, Route, Plus, Trash2, MapPin, Wallet, UserCheck,
} from 'lucide-react';
import { transportApi, type CreateRouteRequest, type CreateVehicleRequest } from '@/api/endpoints/transport';
import { schoolApi } from '@/api/endpoints/school';
import { StaffPicker } from '@/components/pickers/StaffPicker';
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

export default function TransportPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();

  const [routeOpen, setRouteOpen] = useState(false);
  const [vehicleOpen, setVehicleOpen] = useState(false);

  const routesQ = useQuery({
    queryKey: ['transport-routes', tenantId],
    queryFn: () => transportApi.listRoutes(tenantId),
    enabled: !!tenantId,
    retry: false,
  });
  const vehiclesQ = useQuery({
    queryKey: ['transport-vehicles', tenantId],
    queryFn: () => transportApi.listVehicles(tenantId),
    enabled: !!tenantId,
    retry: false,
  });
  const staffQ = useQuery({
    queryKey: ['staff', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    enabled: !!tenantId,
  });

  const routeById = useMemo(() => {
    const m = new Map<string, string>();
    for (const r of routesQ.data ?? []) m.set(r.id, r.name);
    return m;
  }, [routesQ.data]);

  const driverById = useMemo(() => {
    const m = new Map<string, string>();
    for (const s of staffQ.data ?? []) if (s.id) m.set(s.id, s.displayName);
    return m;
  }, [staffQ.data]);

  const deleteRoute = useMutation({
    mutationFn: (id: string) => transportApi.deactivateRoute(tenantId, id),
    onSuccess: () => {
      toast.success('Route deactivated');
      qc.invalidateQueries({ queryKey: ['transport-routes', tenantId] });
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not deactivate'),
  });

  if (routesQ.isError && hasCode(routesQ.error, 'FEATURE_DISABLED')) {
    return (
      <div className="space-y-6">
        <PageHeader title="Transport" icon={<Bus size={18} />} />
        <EmptyState
          icon={<Bus size={28} />}
          title="Transport isn't enabled for your plan"
          description="Manage routes, vehicles, drivers and student bus assignments."
        />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <PageHeader
        title="Transport"
        description="Routes, vehicles, drivers, and student assignments"
        icon={<Bus size={18} />}
      />

      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Stat label="Active routes"   value={(routesQ.data ?? []).length}
              icon={<Route size={16} />} tone="primary" />
        <Stat label="Vehicles"        value={(vehiclesQ.data ?? []).length}
              icon={<Bus size={16} />}   tone="accent" />
        <Stat label="Total capacity"  value={(vehiclesQ.data ?? []).reduce((a, v) => a + v.capacity, 0)}
              icon={<UserCheck size={16} />} tone="info" />
      </div>

      {/* Routes */}
      <Card padding="none" className="overflow-hidden">
        <CardHeader className="flex items-center justify-between">
          <div>
            <CardTitle>Routes</CardTitle>
            <CardDescription>Pickup paths with their stops and fare</CardDescription>
          </div>
          <RequireRole roles={OWNER_OR_ADMIN}>
            <Button size="sm" onClick={() => setRouteOpen(true)}><Plus size={14} /> Add route</Button>
          </RequireRole>
        </CardHeader>

        {routesQ.isLoading && <CardBody><Skeleton className="h-24" /></CardBody>}
        {routesQ.data && routesQ.data.length === 0 && (
          <CardBody><p className="text-sm text-slate-500 py-6 text-center">No routes yet.</p></CardBody>
        )}
        {routesQ.data && routesQ.data.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {routesQ.data.map((r) => (
              <li key={r.id} className="px-5 py-3 flex items-center justify-between">
                <div className="min-w-0 flex-1 flex items-center gap-3">
                  <span className="w-9 h-9 rounded-brand bg-primary-soft text-primary grid place-items-center">
                    <Route size={16} />
                  </span>
                  <div className="min-w-0">
                    <div className="text-sm font-semibold text-slate-900">{r.name}</div>
                    <div className="text-xs text-slate-500 flex items-center gap-3">
                      <span className="inline-flex items-center gap-1">
                        <MapPin size={11} /> {Array.isArray(r.stops) ? r.stops.length : 0} stops
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <Wallet size={11} /> ₹{(r.farePaise / 100).toLocaleString('en-IN')}/month
                      </span>
                    </div>
                  </div>
                </div>
                <RequireRole roles={OWNER_OR_ADMIN}>
                  <button onClick={() => {
                    if (confirm(`Deactivate route "${r.name}"?`)) deleteRoute.mutate(r.id);
                  }} className="text-slate-400 hover:text-danger p-1 rounded transition">
                    <Trash2 size={14} />
                  </button>
                </RequireRole>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {/* Vehicles */}
      <Card padding="none" className="overflow-hidden">
        <CardHeader className="flex items-center justify-between">
          <div>
            <CardTitle>Vehicles</CardTitle>
            <CardDescription>Buses + their assigned routes and drivers</CardDescription>
          </div>
          <RequireRole roles={OWNER_OR_ADMIN}>
            <Button size="sm" onClick={() => setVehicleOpen(true)}><Plus size={14} /> Add vehicle</Button>
          </RequireRole>
        </CardHeader>

        {vehiclesQ.isLoading && <CardBody><Skeleton className="h-24" /></CardBody>}
        {vehiclesQ.data && vehiclesQ.data.length === 0 && (
          <CardBody><p className="text-sm text-slate-500 py-6 text-center">No vehicles yet.</p></CardBody>
        )}
        {vehiclesQ.data && vehiclesQ.data.length > 0 && (
          <ul className="divide-y divide-slate-100">
            {vehiclesQ.data.map((v) => (
              <li key={v.id} className="px-5 py-3 flex items-center justify-between">
                <div className="min-w-0 flex-1 flex items-center gap-3">
                  <span className="w-9 h-9 rounded-brand bg-accent-soft text-accent grid place-items-center">
                    <Bus size={16} />
                  </span>
                  <div className="min-w-0">
                    <div className="text-sm font-semibold text-slate-900 font-mono">{v.registrationNo}</div>
                    <div className="text-xs text-slate-500 flex items-center gap-3">
                      <span>Capacity {v.capacity}</span>
                      {v.routeId && <Badge tone="primary" size="sm">{routeById.get(v.routeId) ?? 'Route'}</Badge>}
                      {v.driverStaffId && (
                        <span className="inline-flex items-center gap-1">
                          <UserCheck size={11} /> {driverById.get(v.driverStaffId) ?? 'Driver'}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <CreateRouteModal
        open={routeOpen}
        onClose={() => setRouteOpen(false)}
        tenantId={tenantId}
      />
      <CreateVehicleModal
        open={vehicleOpen}
        onClose={() => setVehicleOpen(false)}
        tenantId={tenantId}
        routes={routesQ.data ?? []}
      />
    </div>
  );
}

function CreateRouteModal({ open, onClose, tenantId }: {
  open: boolean; onClose: () => void; tenantId: string;
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const initial: CreateRouteRequest = { name: '', farePaise: 0, stops: [] };
  const [form, setForm] = useState(initial);
  const [stopsText, setStopsText] = useState('');
  const [fareRupees, setFareRupees] = useState('');

  const create = useMutation({
    mutationFn: () => transportApi.createRoute(tenantId, {
      name: form.name,
      farePaise: Math.round(Number(fareRupees || 0) * 100),
      stops: stopsText.split('\n').map((s) => s.trim()).filter(Boolean).map((s) => ({ name: s })),
    }),
    onSuccess: () => {
      toast.success('Route created');
      qc.invalidateQueries({ queryKey: ['transport-routes', tenantId] });
      onClose();
      setForm(initial); setStopsText(''); setFareRupees('');
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not create'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Add transport route">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        <Input label="Route name" required value={form.name}
               placeholder="e.g. North Loop"
               onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <Input label="Monthly fare (₹)" type="number" min="0" required value={fareRupees}
               onChange={(e) => setFareRupees(e.target.value)} />
        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Stops</span>
          <textarea
            value={stopsText}
            onChange={(e) => setStopsText(e.target.value)}
            rows={5}
            placeholder="One stop per line — Marathahalli, HSR Layout, Koramangala…"
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          />
        </label>
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={create.isPending} disabled={!form.name}>Create route</Button>
        </div>
      </form>
    </Modal>
  );
}

function CreateVehicleModal({ open, onClose, tenantId, routes }: {
  open: boolean; onClose: () => void; tenantId: string;
  routes: Array<{ id: string; name: string }>;
}) {
  const qc = useQueryClient();
  const toast = useToast();
  const initial: CreateVehicleRequest = { registrationNo: '', capacity: 30 };
  const [form, setForm] = useState(initial);

  const create = useMutation({
    mutationFn: () => transportApi.createVehicle(tenantId, form),
    onSuccess: () => {
      toast.success('Vehicle added');
      qc.invalidateQueries({ queryKey: ['transport-vehicles', tenantId] });
      onClose();
      setForm(initial);
    },
    onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not add'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Add vehicle">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        <Input label="Registration number" required value={form.registrationNo}
               placeholder="e.g. KA-05-AB-1234"
               onChange={(e) => setForm({ ...form, registrationNo: e.target.value })} />
        <Input label="Capacity" type="number" min={1} required value={form.capacity}
               onChange={(e) => setForm({ ...form, capacity: Number(e.target.value) })} />
        <label className="block">
          <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Route</span>
          <select value={form.routeId ?? ''}
            onChange={(e) => setForm({ ...form, routeId: e.target.value || undefined })}
            className="mt-1.5 block w-full rounded-brand border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
          >
            <option value="">— No route assigned —</option>
            {routes.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
          </select>
        </label>
        <StaffPicker
          tenantId={tenantId}
          label="Driver (staff)"
          value={form.driverStaffId ?? null}
          onChange={(id) => setForm({ ...form, driverStaffId: id ?? undefined })}
        />
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={create.isPending} disabled={!form.registrationNo}>
            Add vehicle
          </Button>
        </div>
      </form>
    </Modal>
  );
}
