'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { Plus, Search } from 'lucide-react';
import { studentsApi } from '@/api/endpoints/students';
import { schoolApi } from '@/api/endpoints/school';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import { isApiError } from '@/api/errors';
import type { ClassResponse, CreateStudentRequest, StudentResponse } from '@/types/domain';

const PAGE_SIZE = 25;

export default function StudentsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();

  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [createOpen, setCreateOpen] = useState(false);

  const list = useQuery({
    queryKey: ['students', tenantId, { search, page, size: PAGE_SIZE }],
    queryFn: () => studentsApi.list(tenantId, { search: search || undefined, page, size: PAGE_SIZE }),
    enabled: !!tenantId,
    placeholderData: keepPreviousData,
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Students</h1>
          <p className="text-sm text-slate-500">
            {list.data ? `${list.data.meta.total ?? list.data.items.length} students` : ' '}
          </p>
        </div>
        <RequireRole roles={OWNER_OR_ADMIN}>
          <Button onClick={() => setCreateOpen(true)}>
            <Plus size={16} className="mr-1" /> New student
          </Button>
        </RequireRole>
      </div>

      <div className="relative">
        <Search className="absolute left-3 top-2.5 text-slate-400" size={16} />
        <input
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          placeholder="Search by name or admission number…"
          className="pl-9 pr-3 py-2 w-full max-w-md rounded border border-slate-300 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
        />
      </div>

      {list.isLoading && (
        <div className="text-slate-500 text-sm flex items-center gap-2"><Spinner /> Loading…</div>
      )}
      {list.isError && <ErrorBanner error={list.error} onRetry={() => list.refetch()} />}

      {list.data && list.data.items.length === 0 && (
        <div className="bg-white border border-slate-200 rounded-lg p-10 text-center text-slate-500">
          {search ? 'No matches.' : 'No students yet. Add your first one.'}
        </div>
      )}

      {list.data && list.data.items.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-600 text-xs uppercase">
              <tr>
                <th className="text-left px-4 py-2 font-medium">Name</th>
                <th className="text-left px-4 py-2 font-medium">Admission no.</th>
                <th className="text-left px-4 py-2 font-medium">Gender</th>
                <th className="text-left px-4 py-2 font-medium">DOB</th>
                <th className="text-right px-4 py-2 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {list.data.items.map((s) => (
                <StudentRow key={s.id} tenantId={tenantId} student={s} />
              ))}
            </tbody>
          </table>
          <Pagination
            page={page}
            size={PAGE_SIZE}
            total={list.data.meta.total ?? list.data.items.length}
            onPrev={() => setPage((p) => Math.max(0, p - 1))}
            onNext={() => setPage((p) => p + 1)}
          />
        </div>
      )}

      <CreateStudentModal
        open={createOpen}
        tenantId={tenantId}
        onClose={() => setCreateOpen(false)}
        onCreated={() => {
          setCreateOpen(false);
          qc.invalidateQueries({ queryKey: ['students', tenantId] });
        }}
      />
    </div>
  );
}

function StudentRow({ tenantId, student }: { tenantId: string; student: StudentResponse }) {
  return (
    <tr className="border-t border-slate-100 hover:bg-slate-50">
      <td className="px-4 py-2">
        <Link href={`/tenants/${tenantId}/students/${student.id}`} className="text-primary hover:underline">
          {student.displayName}
        </Link>
      </td>
      <td className="px-4 py-2 text-slate-600">{student.admissionNumber ?? '—'}</td>
      <td className="px-4 py-2 text-slate-600">{student.gender ?? '—'}</td>
      <td className="px-4 py-2 text-slate-600">{student.dateOfBirth ?? '—'}</td>
      <td className="px-4 py-2 text-right">
        {student.active ? (
          <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded">Active</span>
        ) : (
          <span className="text-xs bg-slate-100 text-slate-500 px-2 py-0.5 rounded">Inactive</span>
        )}
      </td>
    </tr>
  );
}

function Pagination({ page, size, total, onPrev, onNext }: {
  page: number; size: number; total: number; onPrev: () => void; onNext: () => void;
}) {
  const start = page * size + 1;
  const end = Math.min((page + 1) * size, total);
  return (
    <div className="px-4 py-2 border-t border-slate-200 text-xs text-slate-500 flex items-center justify-between bg-slate-50">
      <div>{start}–{end} of {total}</div>
      <div className="flex gap-2">
        <Button size="sm" variant="secondary" onClick={onPrev} disabled={page === 0}>Prev</Button>
        <Button size="sm" variant="secondary" onClick={onNext} disabled={end >= total}>Next</Button>
      </div>
    </div>
  );
}

function CreateStudentModal({ open, tenantId, onClose, onCreated }: {
  open: boolean; tenantId: string; onClose: () => void; onCreated: () => void;
}) {
  const [form, setForm] = useState<CreateStudentRequest>({
    firstName: '',
    sectionId: '',
    parentPhone: '',
  });
  const [error, setError] = useState<unknown>(null);

  const classes = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: open && !!tenantId,
    staleTime: 5 * 60_000,
  });

  const create = useMutation({
    mutationFn: (req: CreateStudentRequest) => studentsApi.create(tenantId, req),
    onSuccess: onCreated,
    onError: (e) => setError(e),
  });

  const sectionsFlat: { id: string; label: string }[] =
    classes.data?.flatMap((c: ClassResponse) =>
      c.sections.map((s) => ({ id: s.id, label: `${c.name} · ${s.name}` })),
    ) ?? [];

  return (
    <Modal open={open} onClose={onClose} title="New student">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError(null);
          create.mutate(form);
        }}
        className="space-y-3"
      >
        {error != null && <ErrorBanner error={error} />}

        <Input
          label="First name"
          value={form.firstName}
          onChange={(e) => setForm({ ...form, firstName: e.target.value })}
          required
        />
        <Input
          label="Last name"
          value={form.lastName ?? ''}
          onChange={(e) => setForm({ ...form, lastName: e.target.value })}
        />

        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">Class · Section</span>
          <select
            value={form.sectionId}
            onChange={(e) => setForm({ ...form, sectionId: e.target.value })}
            required
            className="block w-full rounded border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
          >
            <option value="">Select…</option>
            {sectionsFlat.map((s) => (
              <option key={s.id} value={s.id}>{s.label}</option>
            ))}
          </select>
        </label>

        <Input
          label="Parent phone (WhatsApp-capable)"
          value={form.parentPhone}
          onChange={(e) => setForm({ ...form, parentPhone: e.target.value })}
          type="tel"
          inputMode="numeric"
          required
          placeholder="9876543210"
          hint="Same number on multiple children → siblings linked automatically."
        />
        <Input
          label="Parent name (optional)"
          value={form.parentName ?? ''}
          onChange={(e) => setForm({ ...form, parentName: e.target.value })}
        />
        <Input
          label="Admission number (optional)"
          value={form.admissionNumber ?? ''}
          onChange={(e) => setForm({ ...form, admissionNumber: e.target.value })}
        />

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={create.isPending}>Cancel</Button>
          <Button type="submit" disabled={create.isPending || !form.firstName || !form.sectionId || !form.parentPhone}>
            {create.isPending ? <><Spinner className="mr-2" /> Creating…</> : 'Create student'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
