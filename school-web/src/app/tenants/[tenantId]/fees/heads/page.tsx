'use client';

/**
 * Fee heads management — admin / FEE_WRITER only.
 * Allows creating, renaming, and deactivating fee categories
 * (e.g. Tuition, Transport, Exam, Activity, Library).
 */

import { useState } from 'react';
import Link from 'next/link';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { Plus, Pencil, Trash2, Check, X } from 'lucide-react';
import { feeStructureApi } from '@/api/endpoints/feeStructure';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Skeleton } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import { FEE_WRITER, RequireRole } from '@/auth/RequireRole';

export default function FeeHeadsPage() {
  return (
    <RequireRole roles={FEE_WRITER}>
      <FeeHeadsInner />
    </RequireRole>
  );
}

function FeeHeadsInner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const toast = useToast();

  const [newName, setNewName] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState('');

  const headsQ = useQuery({
    queryKey: ['fee-heads', tenantId],
    queryFn: () => feeStructureApi.listFeeHeads(tenantId),
    enabled: !!tenantId,
  });

  const createM = useMutation({
    mutationFn: () => feeStructureApi.createFeeHead(tenantId, newName.trim()),
    onSuccess: () => {
      toast.success('Fee head created');
      setNewName('');
      qc.invalidateQueries({ queryKey: ['fee-heads', tenantId] });
    },
    onError: () => toast.error('Could not create fee head'),
  });

  const updateM = useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) =>
      feeStructureApi.updateFeeHead(tenantId, id, name),
    onSuccess: () => {
      toast.success('Fee head renamed');
      setEditingId(null);
      qc.invalidateQueries({ queryKey: ['fee-heads', tenantId] });
    },
    onError: () => toast.error('Could not rename fee head'),
  });

  const deactivateM = useMutation({
    mutationFn: (id: string) => feeStructureApi.deactivateFeeHead(tenantId, id),
    onSuccess: () => {
      toast.success('Fee head deactivated');
      qc.invalidateQueries({ queryKey: ['fee-heads', tenantId] });
    },
    onError: () => toast.error('Could not deactivate fee head'),
  });

  const heads = headsQ.data ?? [];
  const active = heads.filter((h) => h.active);
  const inactive = heads.filter((h) => !h.active);

  return (
    <div className="space-y-5 max-w-2xl">
      <div>
        <Link href={`/tenants/${tenantId}/fees/dashboard`}
              className="text-sm text-slate-500 hover:underline">
          ← Fees dashboard
        </Link>
        <h1 className="text-2xl font-semibold mt-1">Fee heads</h1>
        <p className="text-sm text-slate-500">
          Categories of fees (e.g. Tuition, Transport, Exam). Used when creating fee structures.
        </p>
      </div>

      {/* Create new */}
      <Card>
        <CardHeader>
          <CardTitle>Add fee head</CardTitle>
        </CardHeader>
        <CardBody>
          <form
            onSubmit={(e) => { e.preventDefault(); if (newName.trim()) createM.mutate(); }}
            className="flex gap-2"
          >
            <Input
              placeholder="e.g. Tuition, Transport, Exam, Library…"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              className="flex-1"
            />
            <Button type="submit" disabled={!newName.trim() || createM.isPending} loading={createM.isPending}>
              <Plus size={14} /> Add
            </Button>
          </form>
        </CardBody>
      </Card>

      {/* Active heads */}
      <Card className="p-0 overflow-hidden">
        <CardHeader>
          <CardTitle>Active fee heads</CardTitle>
          <CardDescription>Used in fee structures and invoices</CardDescription>
        </CardHeader>
        <CardBody className="p-0">
          {headsQ.isLoading && (
            <div className="p-5 space-y-2">
              {[1,2,3].map((i) => <Skeleton key={i} className="h-10 w-full" />)}
            </div>
          )}
          {headsQ.isError && <div className="p-5"><ErrorBanner error={headsQ.error} /></div>}
          {!headsQ.isLoading && active.length === 0 && (
            <p className="p-5 text-sm text-slate-500">No fee heads yet. Add one above.</p>
          )}
          {active.map((h, idx) => (
            <div
              key={h.id}
              className={`flex items-center gap-3 px-5 py-3 ${idx > 0 ? 'border-t border-slate-100' : ''}`}
            >
              {editingId === h.id ? (
                <>
                  <Input
                    value={editName}
                    onChange={(e) => setEditName(e.target.value)}
                    className="flex-1 h-8"
                    autoFocus
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && editName.trim()) updateM.mutate({ id: h.id, name: editName.trim() });
                      if (e.key === 'Escape') setEditingId(null);
                    }}
                  />
                  <button
                    type="button"
                    onClick={() => { if (editName.trim()) updateM.mutate({ id: h.id, name: editName.trim() }); }}
                    className="p-1.5 rounded text-success hover:bg-success/10"
                    title="Save"
                  >
                    <Check size={15} />
                  </button>
                  <button
                    type="button"
                    onClick={() => setEditingId(null)}
                    className="p-1.5 rounded text-slate-400 hover:bg-slate-100"
                    title="Cancel"
                  >
                    <X size={15} />
                  </button>
                </>
              ) : (
                <>
                  <span className="flex-1 text-sm font-medium text-slate-800">{h.name}</span>
                  <button
                    type="button"
                    onClick={() => { setEditingId(h.id); setEditName(h.name); }}
                    className="p-1.5 rounded text-slate-400 hover:text-primary hover:bg-primary/10 transition"
                    title="Rename"
                  >
                    <Pencil size={14} />
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      if (window.confirm(`Deactivate "${h.name}"? Historical invoices will keep this head.`)) {
                        deactivateM.mutate(h.id);
                      }
                    }}
                    className="p-1.5 rounded text-slate-400 hover:text-danger hover:bg-danger/10 transition"
                    title="Deactivate"
                  >
                    <Trash2 size={14} />
                  </button>
                </>
              )}
            </div>
          ))}
        </CardBody>
      </Card>

      {/* Inactive heads */}
      {inactive.length > 0 && (
        <Card className="p-0 overflow-hidden">
          <CardHeader>
            <CardTitle>Inactive fee heads</CardTitle>
            <CardDescription>Preserved for historical records only</CardDescription>
          </CardHeader>
          <CardBody className="p-0">
            {inactive.map((h, idx) => (
              <div
                key={h.id}
                className={`flex items-center gap-3 px-5 py-3 ${idx > 0 ? 'border-t border-slate-100' : ''}`}
              >
                <span className="flex-1 text-sm text-slate-400 line-through">{h.name}</span>
                <Badge tone="neutral" size="sm">Inactive</Badge>
              </div>
            ))}
          </CardBody>
        </Card>
      )}
    </div>
  );
}
