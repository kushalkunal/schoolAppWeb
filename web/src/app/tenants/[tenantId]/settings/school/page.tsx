'use client';

import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { schoolApi } from '@/api/endpoints/school';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { OWNER_OR_ADMIN, useHasRole } from '@/auth/RequireRole';
import type { UpdateSchoolRequest } from '@/types/domain';

export default function SchoolSettingsPage() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const canEdit = useHasRole(...OWNER_OR_ADMIN);
  const qc = useQueryClient();

  const q = useQuery({
    queryKey: ['tenant', tenantId],
    queryFn: () => schoolApi.get(tenantId),
    enabled: !!tenantId,
  });

  const [form, setForm] = useState<UpdateSchoolRequest>({});
  const [savedAt, setSavedAt] = useState<number | null>(null);

  // Seed form once when the data lands.
  useEffect(() => {
    if (q.data) {
      setForm({
        name: q.data.name,
        principalName: q.data.principalName,
        city: q.data.city ?? '',
        state: q.data.state,
      });
    }
  }, [q.data]);

  const save = useMutation({
    mutationFn: (req: UpdateSchoolRequest) => schoolApi.update(tenantId, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tenant', tenantId] });
      setSavedAt(Date.now());
    },
  });

  if (q.isLoading) return <div className="text-slate-500 flex items-center gap-2"><Spinner /> Loading…</div>;
  if (q.isError) return <ErrorBanner error={q.error} onRetry={() => q.refetch()} />;

  const s = q.data!;
  return (
    <div className="space-y-4 max-w-2xl">
      <div>
        <h1 className="text-2xl font-semibold">School profile</h1>
        <p className="text-sm text-slate-500">Board, contact, address. Identifier changes need re-verification — separate flow.</p>
      </div>

      {save.isError && <ErrorBanner error={save.error} />}

      <Card>
        <CardHeader className="flex justify-between items-center">
          <CardTitle>{s.name}</CardTitle>
          <span className="text-xs text-slate-500">{s.board} · {s.waConfigured ? 'WhatsApp wired' : 'WhatsApp not set'}</span>
        </CardHeader>
        <CardBody className="space-y-3">
          <Input label="Name" value={form.name ?? ''} disabled={!canEdit}
            onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <Input label="Principal" value={form.principalName ?? ''} disabled={!canEdit}
            onChange={(e) => setForm({ ...form, principalName: e.target.value })} />
          <Input label="State" value={form.state ?? ''} disabled={!canEdit}
            onChange={(e) => setForm({ ...form, state: e.target.value })} />
          <Input label="City" value={form.city ?? ''} disabled={!canEdit}
            onChange={(e) => setForm({ ...form, city: e.target.value })} />
          {canEdit && (
            <div className="flex items-center justify-between pt-2">
              {savedAt && Date.now() - savedAt < 4000 && (
                <span className="text-sm text-green-700">Saved.</span>
              )}
              <Button className="ml-auto" onClick={() => save.mutate(form)} disabled={save.isPending}>
                {save.isPending ? 'Saving…' : 'Save changes'}
              </Button>
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
