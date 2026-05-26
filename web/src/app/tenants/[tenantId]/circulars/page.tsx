'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Megaphone, Plus } from 'lucide-react';
import { circularsApi } from '@/api/endpoints/circulars';
import { schoolApi } from '@/api/endpoints/school';
import { studentsApi } from '@/api/endpoints/students';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { Card } from '@/components/ui/Card';
import { OWNER_OR_ADMIN, RequireRole } from '@/auth/RequireRole';
import type { CircularTargetType, CreateCircularRequest } from '@/types/domain';

export default function CircularsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState(false);
  const size = 20;

  const q = useQuery({
    queryKey: ['circulars', tenantId, page, size],
    queryFn: () => circularsApi.list(tenantId, { page, size }),
    enabled: !!tenantId,
  });

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-semibold">Circulars</h1>
          <p className="text-sm text-slate-500">Broadcasts to parents over WhatsApp. Delivery stats arrive asynchronously.</p>
        </div>
        <RequireRole roles={OWNER_OR_ADMIN}>
          <Button onClick={() => setOpen(true)}><Plus size={16} className="mr-1" /> Compose</Button>
        </RequireRole>
      </div>

      {q.isLoading && <Spinner />}
      {q.isError && <ErrorBanner error={q.error} onRetry={() => q.refetch()} />}

      {q.data && q.data.items.length === 0 && (
        <Card>
          <div className="text-center py-8 text-slate-500">
            <Megaphone className="mx-auto mb-2" size={32} />
            <p>No circulars yet.</p>
          </div>
        </Card>
      )}

      {q.data && q.data.items.length > 0 && (
        <div className="space-y-3">
          {q.data.items.map((c) => (
            <Card key={c.id}>
              <div className="flex justify-between items-start">
                <div className="flex-1">
                  <h3 className="font-medium text-slate-800">{c.title}</h3>
                  <p className="text-sm text-slate-600 mt-1 whitespace-pre-wrap">{c.body}</p>
                  <div className="text-xs text-slate-400 mt-2">
                    {c.targetType} · {new Date(c.createdAt).toLocaleString()}
                    {c.sentAt && ` · sent ${new Date(c.sentAt).toLocaleString()}`}
                  </div>
                </div>
                <div className="text-right text-xs space-y-1 ml-4 shrink-0">
                  <div>Sent: <span className="font-medium text-slate-700">{c.sentCount}</span></div>
                  <div>Delivered: <span className="font-medium text-green-700">{c.deliveredCount}</span></div>
                  <div>Read: <span className="font-medium text-blue-700">{c.readCount}</span></div>
                  {c.failedCount > 0 && <div>Failed: <span className="font-medium text-red-700">{c.failedCount}</span></div>}
                </div>
              </div>
            </Card>
          ))}

          <div className="flex items-center justify-between text-sm">
            <span className="text-slate-500">Page {page + 1}</span>
            <div className="flex gap-2">
              <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>Previous</Button>
              <Button variant="secondary" size="sm" disabled={q.data.items.length < size} onClick={() => setPage((p) => p + 1)}>Next</Button>
            </div>
          </div>
        </div>
      )}

      <ComposeModal
        open={open}
        tenantId={tenantId}
        onClose={() => setOpen(false)}
        onSuccess={() => { setOpen(false); qc.invalidateQueries({ queryKey: ['circulars', tenantId] }); }}
      />
    </div>
  );
}

function ComposeModal({ open, tenantId, onClose, onSuccess }: {
  open: boolean; tenantId: string; onClose: () => void; onSuccess: () => void;
}) {
  const [form, setForm] = useState<CreateCircularRequest>({
    title: '', body: '', targetType: 'ALL_PARENTS', targetIds: [], language: 'en',
  });
  // Slice 36 — replaces the raw UUID textarea with checkbox/chip pickers. Loaded lazily;
  // only the classes/sections lookup runs for CLASSES/SECTIONS, students for STUDENTS.
  const classesQ = useQuery({
    queryKey: ['classes', tenantId],
    queryFn: () => schoolApi.listClasses(tenantId),
    enabled: form.targetType === 'CLASSES' || form.targetType === 'SECTIONS',
    staleTime: 5 * 60_000,
  });
  const studentsQ = useQuery({
    queryKey: ['students-list-for-picker', tenantId],
    queryFn: () => studentsApi.list(tenantId, { size: 500 }),
    enabled: form.targetType === 'STUDENTS',
    staleTime: 60_000,
  });

  const create = useMutation({
    mutationFn: () => circularsApi.create(tenantId, {
      ...form,
      targetIds: form.targetType === 'ALL_PARENTS' ? [] : (form.targetIds ?? []),
    }),
    onSuccess,
  });

  const needsTargets = form.targetType !== 'ALL_PARENTS';
  const selected = new Set(form.targetIds ?? []);
  const toggle = (id: string) => {
    const next = new Set(selected);
    next.has(id) ? next.delete(id) : next.add(id);
    setForm({ ...form, targetIds: Array.from(next) });
  };

  return (
    <Modal open={open} onClose={onClose} title="Compose circular">
      <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }} className="space-y-3">
        {create.isError && <ErrorBanner error={create.error} />}
        <Input label="Title" value={form.title} required maxLength={200}
          onChange={(e) => setForm({ ...form, title: e.target.value })} />
        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">Message body</span>
          <textarea
            value={form.body}
            onChange={(e) => setForm({ ...form, body: e.target.value })}
            rows={6}
            required
            maxLength={4000}
            className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
            placeholder="Use WhatsApp-friendly plain text. *bold*, _italic_, ~strike~ render in WhatsApp."
          />
        </label>
        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">Audience</span>
          <select
            value={form.targetType}
            onChange={(e) => setForm({ ...form, targetType: e.target.value as CircularTargetType })}
            className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="ALL_PARENTS">All parents</option>
            <option value="CLASSES">Specific classes</option>
            <option value="SECTIONS">Specific sections</option>
            <option value="STUDENTS">Specific students</option>
          </select>
        </label>
        {needsTargets && (
          <div className="block">
            <span className="text-sm text-slate-700 mb-1 inline-block">
              Pick {form.targetType === 'CLASSES' ? 'classes' : form.targetType === 'SECTIONS' ? 'sections' : 'students'}
              {selected.size > 0 && <span className="text-xs text-slate-500 ml-2">{selected.size} selected</span>}
            </span>
            <div className="max-h-48 overflow-auto border border-slate-200 rounded p-2 space-y-1">
              {(form.targetType === 'CLASSES' || form.targetType === 'SECTIONS') && (
                <>
                  {classesQ.isLoading && <div className="text-xs text-slate-400">Loading…</div>}
                  {classesQ.data?.flatMap((c) =>
                    form.targetType === 'CLASSES'
                      ? [{ id: c.id, label: c.name }]
                      : c.sections.map((s) => ({ id: s.id, label: `${c.name} · ${s.name}` })),
                  ).map((o) => (
                    <label key={o.id} className="flex items-center gap-2 text-sm cursor-pointer hover:bg-slate-50 rounded px-1">
                      <input type="checkbox" checked={selected.has(o.id)} onChange={() => toggle(o.id)} />
                      <span>{o.label}</span>
                    </label>
                  ))}
                  {classesQ.data && classesQ.data.length === 0 && (
                    <div className="text-xs text-slate-400">No classes configured. Add some under Settings → Classes.</div>
                  )}
                </>
              )}
              {form.targetType === 'STUDENTS' && (
                <>
                  {studentsQ.isLoading && <div className="text-xs text-slate-400">Loading…</div>}
                  {studentsQ.data?.items.map((s) => (
                    <label key={s.id} className="flex items-center gap-2 text-sm cursor-pointer hover:bg-slate-50 rounded px-1">
                      <input type="checkbox" checked={selected.has(s.id)} onChange={() => toggle(s.id)} />
                      <span>{s.displayName}</span>
                      {s.admissionNumber && <span className="text-xs text-slate-400">#{s.admissionNumber}</span>}
                    </label>
                  ))}
                  {studentsQ.data && studentsQ.data.items.length === 0 && (
                    <div className="text-xs text-slate-400">No students yet.</div>
                  )}
                </>
              )}
            </div>
          </div>
        )}
        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">Language</span>
          <select
            value={form.language ?? 'en'}
            onChange={(e) => setForm({ ...form, language: e.target.value })}
            className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="en">English</option>
            <option value="hi">Hindi</option>
            <option value="ta">Tamil</option>
            <option value="te">Telugu</option>
            <option value="mr">Marathi</option>
            <option value="bn">Bengali</option>
          </select>
        </label>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={create.isPending}>Cancel</Button>
          <Button type="submit" disabled={create.isPending || !form.title || !form.body || (needsTargets && selected.size === 0)}>
            {create.isPending ? 'Sending…' : 'Send'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
