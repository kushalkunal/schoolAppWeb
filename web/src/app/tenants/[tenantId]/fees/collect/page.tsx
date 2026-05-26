'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { studentsApi } from '@/api/endpoints/students';
import { feesApi } from '@/api/endpoints/fees';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { formatINR } from '@/lib/utils';
import type { PaymentMode, QuickCollectRequest, StudentResponse } from '@/types/domain';

export default function QuickCollectPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';

  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<StudentResponse | null>(null);
  const [amountRupees, setAmountRupees] = useState('');
  const [mode, setMode] = useState<PaymentMode>('CASH');
  const [notes, setNotes] = useState('');

  // Debounced search — we just rerun the query on each keystroke (React Query will
  // dedupe; for production a useDeferredValue would be nicer).
  const searchQ = useQuery({
    queryKey: ['students', tenantId, { search, size: 10 }],
    queryFn: () => studentsApi.list(tenantId, { search, size: 10 }),
    enabled: !!tenantId && search.length >= 2 && !selected,
  });

  const collect = useMutation({
    mutationFn: (req: QuickCollectRequest) => feesApi.quickCollect(tenantId, req),
  });

  function reset() {
    setSelected(null);
    setSearch('');
    setAmountRupees('');
    setMode('CASH');
    setNotes('');
    collect.reset();
  }

  function submit() {
    if (!selected) return;
    const amountPaise = Math.round(Number(amountRupees) * 100);
    if (!Number.isFinite(amountPaise) || amountPaise <= 0) return;
    const req: QuickCollectRequest = {
      studentId: selected.id,
      amountPaise,
      paymentMode: mode,
    };
    if (notes.trim()) req.notes = notes.trim();
    collect.mutate(req);
  }

  return (
    <div className="space-y-4 max-w-2xl">
      <div>
        <Link href={`/tenants/${tenantId}/fees/dashboard`} className="text-sm text-slate-500 hover:underline">
          ← Fees dashboard
        </Link>
        <h1 className="text-2xl font-semibold mt-1">Quick collect</h1>
        <p className="text-sm text-slate-500">Find a student, take payment, parent gets a WhatsApp receipt.</p>
      </div>

      {collect.isSuccess && collect.data && (
        <Card>
          <CardBody className="bg-green-50 border-green-200">
            <div className="text-green-800">
              <p className="font-semibold">Receipt {collect.data.receiptNumber} created</p>
              <p className="text-sm">
                {formatINR(collect.data.amountPaise)} collected. Outstanding balance now {formatINR(collect.data.outstandingBalancePaise)}.
              </p>
              {collect.data.receiptPdfUrl && (
                <a href={collect.data.receiptPdfUrl} target="_blank" rel="noreferrer"
                  className="text-sm text-primary hover:underline">
                  View receipt PDF →
                </a>
              )}
              <div className="mt-3">
                <Button size="sm" onClick={reset}>Collect another</Button>
              </div>
            </div>
          </CardBody>
        </Card>
      )}

      {collect.isError && <ErrorBanner error={collect.error} />}

      {!collect.isSuccess && (
        <Card>
          <CardHeader><CardTitle>Step 1 · Find student</CardTitle></CardHeader>
          <CardBody className="space-y-3">
            {selected ? (
              <div className="flex items-center justify-between p-3 border border-primary bg-blue-50 rounded">
                <div>
                  <div className="font-medium">{selected.displayName}</div>
                  <div className="text-xs text-slate-500">{selected.admissionNumber ?? '—'}</div>
                </div>
                <Button variant="ghost" size="sm" onClick={() => setSelected(null)}>Change</Button>
              </div>
            ) : (
              <>
                <Input
                  placeholder="Search by name or admission number…"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  autoFocus
                />
                {searchQ.isFetching && <Spinner />}
                {searchQ.data && searchQ.data.items.length > 0 && (
                  <ul className="border border-slate-200 rounded divide-y divide-slate-100">
                    {searchQ.data.items.map((s) => (
                      <li key={s.id}>
                        <button
                          type="button"
                          onClick={() => setSelected(s)}
                          className="w-full text-left px-3 py-2 hover:bg-slate-50 text-sm"
                        >
                          <div className="font-medium">{s.displayName}</div>
                          <div className="text-xs text-slate-500">{s.admissionNumber ?? '—'}</div>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </>
            )}
          </CardBody>
        </Card>
      )}

      {selected && !collect.isSuccess && (
        <Card>
          <CardHeader><CardTitle>Step 2 · Collect payment</CardTitle></CardHeader>
          <CardBody className="space-y-3">
            <Input
              label="Amount (₹)"
              value={amountRupees}
              onChange={(e) => setAmountRupees(e.target.value.replace(/[^0-9.]/g, ''))}
              type="text"
              inputMode="decimal"
              placeholder="4500.00"
              autoFocus
            />
            <div>
              <div className="text-sm text-slate-700 mb-1">Payment mode</div>
              <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
                {(['CASH', 'ONLINE', 'CHEQUE', 'DD', 'BANK_TRANSFER'] as const).map((m) => (
                  <button
                    key={m}
                    type="button"
                    onClick={() => setMode(m)}
                    className={`py-2 text-xs rounded border ${mode === m ? 'bg-primary text-white border-primary' : 'border-slate-300 bg-white hover:bg-slate-50'}`}
                  >
                    {m}
                  </button>
                ))}
              </div>
            </div>
            <Input
              label="Notes (optional)"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Term 2 fees, includes transport"
            />
            <div className="flex justify-end">
              <Button onClick={submit} disabled={collect.isPending || !amountRupees || Number(amountRupees) <= 0}>
                {collect.isPending ? <><Spinner className="mr-2" /> Collecting…</> : 'Collect & send receipt'}
              </Button>
            </div>
          </CardBody>
        </Card>
      )}
    </div>
  );
}
