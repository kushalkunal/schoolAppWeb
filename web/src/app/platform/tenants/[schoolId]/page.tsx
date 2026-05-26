'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { platformApi } from '@/api/endpoints/platform';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';
import { Modal } from '@/components/ui/Modal';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import type { Plan, TenantProviderConfig } from '@/types/domain';

const CONCERNS = ['WHATSAPP', 'EMAIL', 'PAYMENT', 'STORAGE', 'OCR', 'LLM'] as const;
type Concern = (typeof CONCERNS)[number];

export default function PlatformTenantDetailPage() {
  const params = useParams();
  const schoolId = typeof params.schoolId === 'string' ? params.schoolId : '';
  const qc = useQueryClient();

  const detail = useQuery({
    queryKey: ['platform', 'tenant', schoolId],
    queryFn: () => platformApi.getTenant(schoolId),
    enabled: !!schoolId,
  });
  const features = useQuery({
    queryKey: ['platform', 'features'],
    queryFn: () => platformApi.listFeatures(),
  });
  const plans = useQuery({
    queryKey: ['platform', 'plans'],
    queryFn: () => platformApi.listPlans(),
  });
  const providers = useQuery({
    queryKey: ['platform', 'tenant', schoolId, 'providers'],
    queryFn: () => platformApi.listProviders(schoolId),
    enabled: !!schoolId,
  });

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['platform', 'tenant', schoolId] });
    qc.invalidateQueries({ queryKey: ['platform', 'tenants'] });
  };

  const resume = useMutation({
    mutationFn: () => platformApi.resume(schoolId),
    onSuccess: invalidate,
  });

  const [planOpen, setPlanOpen] = useState(false);
  const [suspendOpen, setSuspendOpen] = useState(false);

  if (detail.isLoading) return <Spinner />;
  if (detail.isError) return <ErrorBanner error={detail.error} onRetry={() => detail.refetch()} />;
  if (!detail.data) return null;

  const t = detail.data.summary;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-start">
        <div>
          <h1 className="text-2xl font-semibold">{t.name}</h1>
          <p className="text-sm text-slate-500">{t.state ?? '—'} · {t.board ?? 'No board'} · {t.phone ?? '—'}</p>
          <p className="text-xs text-slate-400 mt-1">{t.schoolId}</p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setPlanOpen(true)}>Change plan</Button>
          {t.status === 'SUSPENDED' ? (
            <Button onClick={() => resume.mutate()} disabled={resume.isPending}>Resume</Button>
          ) : (
            <Button variant="danger" onClick={() => setSuspendOpen(true)}>Suspend</Button>
          )}
        </div>
      </div>

      {resume.isError && <ErrorBanner error={resume.error} />}

      <div className="grid grid-cols-3 gap-4">
        <Card>
          <div className="text-xs text-slate-500 uppercase">Plan</div>
          <div className="text-xl font-semibold">{t.planCode ?? '—'}</div>
          <div className="text-sm text-slate-600">{t.status ?? '—'}</div>
        </Card>
        <Card>
          <div className="text-xs text-slate-500 uppercase">Subscription</div>
          <div className="text-sm text-slate-700 mt-1 break-all">{t.subscriptionId ?? 'No subscription'}</div>
          {t.trialEndsAt && (
            <div className="text-xs text-slate-500 mt-1">Trial ends {new Date(t.trialEndsAt).toLocaleDateString()}</div>
          )}
        </Card>
        <Card>
          <div className="text-xs text-slate-500 uppercase">Created</div>
          <div className="text-sm text-slate-700 mt-1">{new Date(t.createdAt).toLocaleDateString()}</div>
          <div className="text-xs text-slate-500">{t.active ? 'Active' : 'Inactive'}</div>
        </Card>
      </div>

      <Card>
        <h2 className="text-lg font-semibold mb-3">Usage</h2>
        {Object.keys(detail.data.usage).length === 0 ? (
          <p className="text-sm text-slate-500">No usage recorded for the current period.</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="text-xs text-slate-500 uppercase border-b border-slate-200">
              <tr>
                <th className="text-left py-2">Metric</th>
                <th className="text-right py-2">Used</th>
                <th className="text-right py-2">Limit</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(detail.data.usage).map(([metric, used]) => {
                const limit = detail.data!.limits[metric];
                return (
                  <tr key={metric} className="border-t border-slate-100">
                    <td className="py-2 text-slate-700">{metric}</td>
                    <td className="py-2 text-right">{used}</td>
                    <td className="py-2 text-right text-slate-500">{limit === -1 ? 'unlimited' : (limit ?? '—')}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </Card>

      <Card>
        <h2 className="text-lg font-semibold mb-3">Feature overrides</h2>
        <p className="text-sm text-slate-500 mb-3">
          Overrides take precedence over the plan&apos;s default. Toggling here flips it for this tenant only.
        </p>
        {features.isLoading && <Spinner />}
        {features.data && (
          <div className="grid grid-cols-2 gap-2">
            {features.data.map((f) => {
              const enabled = detail.data!.features[f.featureKey] ?? f.defaultEnabled;
              return (
                <FeatureToggle
                  key={f.featureKey}
                  schoolId={schoolId}
                  featureKey={f.featureKey}
                  name={f.name}
                  enabled={enabled}
                  onChange={invalidate}
                />
              );
            })}
          </div>
        )}
      </Card>

      <Card>
        <h2 className="text-lg font-semibold mb-3">Provider configs</h2>
        <p className="text-sm text-slate-500 mb-3">
          Per-tenant credentials override platform-wide env defaults. Verify after saving.
        </p>
        {providers.isLoading && <Spinner />}
        {providers.data && (
          <div className="space-y-2">
            {CONCERNS.map((concern) => (
              <ProviderRow
                key={concern}
                schoolId={schoolId}
                concern={concern}
                existing={providers.data!.find((p) => p.concern === concern)}
                onChange={() => qc.invalidateQueries({ queryKey: ['platform', 'tenant', schoolId, 'providers'] })}
              />
            ))}
          </div>
        )}
      </Card>

      <ChangePlanModal
        open={planOpen}
        schoolId={schoolId}
        currentPlan={t.planCode}
        plans={plans.data ?? []}
        onClose={() => setPlanOpen(false)}
        onSuccess={() => { setPlanOpen(false); invalidate(); }}
      />

      <SuspendModal
        open={suspendOpen}
        schoolId={schoolId}
        onClose={() => setSuspendOpen(false)}
        onSuccess={() => { setSuspendOpen(false); invalidate(); }}
      />
    </div>
  );
}

function FeatureToggle({ schoolId, featureKey, name, enabled, onChange }: {
  schoolId: string; featureKey: string; name: string; enabled: boolean; onChange: () => void;
}) {
  const m = useMutation({
    mutationFn: (next: boolean) => platformApi.setFeatureOverride(schoolId, featureKey, { enabled: next }),
    onSuccess: onChange,
  });
  const clear = useMutation({
    mutationFn: () => platformApi.clearFeatureOverride(schoolId, featureKey),
    onSuccess: onChange,
  });
  return (
    <div className="flex items-center justify-between border border-slate-200 rounded p-2 text-sm">
      <div>
        <div className="font-medium text-slate-800">{name}</div>
        <code className="text-xs text-slate-400">{featureKey}</code>
      </div>
      <div className="flex items-center gap-2">
        <button
          onClick={() => m.mutate(!enabled)}
          disabled={m.isPending}
          className={`px-2 py-1 rounded text-xs font-medium ${enabled ? 'bg-green-100 text-green-800' : 'bg-slate-200 text-slate-700'}`}
        >
          {enabled ? 'On' : 'Off'}
        </button>
        <button
          onClick={() => clear.mutate()}
          disabled={clear.isPending}
          className="text-xs text-slate-400 hover:text-slate-600"
          title="Clear override (revert to plan default)"
        >
          reset
        </button>
      </div>
    </div>
  );
}

function ProviderRow({ schoolId, concern, existing, onChange }: {
  schoolId: string; concern: Concern; existing: TenantProviderConfig | undefined; onChange: () => void;
}) {
  const [open, setOpen] = useState(false);
  const verify = useMutation({
    mutationFn: () => platformApi.verifyProvider(schoolId, concern),
    onSuccess: onChange,
  });
  const clear = useMutation({
    mutationFn: () => platformApi.clearProvider(schoolId, concern),
    onSuccess: onChange,
  });
  return (
    <div className="flex items-center justify-between border border-slate-200 rounded p-3 text-sm">
      <div className="flex-1">
        <div className="font-medium text-slate-800">{concern}</div>
        {existing ? (
          <div className="text-xs text-slate-500">
            {existing.provider} ·{' '}
            {existing.verifiedAt
              ? <span className="text-green-700">verified {new Date(existing.verifiedAt).toLocaleString()}</span>
              : <span className="text-amber-700">not verified</span>}
            {existing.lastError && <span className="text-red-600"> · {existing.lastError}</span>}
          </div>
        ) : (
          <div className="text-xs text-slate-400">env fallback</div>
        )}
      </div>
      <div className="flex gap-2">
        {existing && (
          <>
            <Button variant="ghost" size="sm" onClick={() => verify.mutate()} disabled={verify.isPending}>Verify</Button>
            <Button variant="ghost" size="sm" onClick={() => clear.mutate()} disabled={clear.isPending}>Clear</Button>
          </>
        )}
        <Button variant="secondary" size="sm" onClick={() => setOpen(true)}>
          {existing ? 'Edit' : 'Set'}
        </Button>
      </div>
      <ProviderModal
        open={open}
        schoolId={schoolId}
        concern={concern}
        existing={existing}
        onClose={() => setOpen(false)}
        onSuccess={() => { setOpen(false); onChange(); }}
      />
    </div>
  );
}

function ProviderModal({ open, schoolId, concern, existing, onClose, onSuccess }: {
  open: boolean; schoolId: string; concern: Concern; existing: TenantProviderConfig | undefined;
  onClose: () => void; onSuccess: () => void;
}) {
  const [provider, setProvider] = useState(existing?.provider ?? '');
  const [json, setJson] = useState(existing ? JSON.stringify(existing.config, null, 2) : '{\n  \n}');
  const [parseError, setParseError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () => {
      let config: Record<string, unknown>;
      try { config = JSON.parse(json); } catch (e) { throw new Error('Config is not valid JSON'); }
      return platformApi.setProvider(schoolId, concern, { provider, config });
    },
    onSuccess,
  });

  return (
    <Modal open={open} onClose={onClose} title={`${existing ? 'Edit' : 'Set'} ${concern} provider`}>
      <form onSubmit={(e) => { e.preventDefault(); setParseError(null); try { JSON.parse(json); save.mutate(); } catch (err) { setParseError((err as Error).message); } }} className="space-y-3">
        {save.isError && <ErrorBanner error={save.error} />}
        {parseError && <ErrorBanner error={parseError} />}
        <Input
          label="Provider key"
          value={provider}
          required
          placeholder={defaultProviderPlaceholder(concern)}
          onChange={(e) => setProvider(e.target.value)}
        />
        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">Config (JSON)</span>
          <textarea
            value={json}
            onChange={(e) => setJson(e.target.value)}
            rows={10}
            className="block w-full rounded border border-slate-300 px-3 py-2 text-xs font-mono"
            spellCheck={false}
          />
        </label>
        <p className="text-xs text-slate-500">
          Secrets are encrypted at rest. Required keys vary by provider — see docs/CONFIGURATION.md.
        </p>
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={save.isPending}>Cancel</Button>
          <Button type="submit" disabled={save.isPending || !provider}>
            {save.isPending ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function defaultProviderPlaceholder(concern: Concern): string {
  switch (concern) {
    case 'WHATSAPP': return 'WATI';
    case 'EMAIL':    return 'SMTP';
    case 'PAYMENT':  return 'STRIPE | RAZORPAY';
    case 'STORAGE':  return 'S3';
    case 'OCR':      return 'TESSERACT | GOOGLE_VISION';
    case 'LLM':      return 'OPENAI | ANTHROPIC';
  }
}

function ChangePlanModal({ open, schoolId, currentPlan, plans, onClose, onSuccess }: {
  open: boolean; schoolId: string; currentPlan: string | null; plans: Plan[]; onClose: () => void; onSuccess: () => void;
}) {
  const [planCode, setPlanCode] = useState(currentPlan ?? '');
  const [note, setNote] = useState('');

  const m = useMutation({
    mutationFn: () => platformApi.changePlan(schoolId, { planCode, note: note || undefined }),
    onSuccess,
  });

  return (
    <Modal open={open} onClose={onClose} title="Change plan">
      <form onSubmit={(e) => { e.preventDefault(); m.mutate(); }} className="space-y-3">
        {m.isError && <ErrorBanner error={m.error} />}
        <label className="block">
          <span className="text-sm text-slate-700 mb-1 inline-block">New plan</span>
          <select
            value={planCode}
            onChange={(e) => setPlanCode(e.target.value)}
            className="block w-full rounded border border-slate-300 px-3 py-2 text-sm"
            required
          >
            <option value="" disabled>Select a plan</option>
            {plans.filter((p) => p.active).map((p) => (
              <option key={p.code} value={p.code}>
                {p.name} · ₹{(p.monthlyPricePaise / 100).toLocaleString('en-IN')}/mo
              </option>
            ))}
          </select>
        </label>
        <Input label="Note (audit log)" value={note} onChange={(e) => setNote(e.target.value)} placeholder="Reason for change" />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={m.isPending}>Cancel</Button>
          <Button type="submit" disabled={m.isPending || !planCode}>
            {m.isPending ? 'Updating…' : 'Change plan'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function SuspendModal({ open, schoolId, onClose, onSuccess }: {
  open: boolean; schoolId: string; onClose: () => void; onSuccess: () => void;
}) {
  const [reason, setReason] = useState('');
  const m = useMutation({
    mutationFn: () => platformApi.suspend(schoolId, { reason }),
    onSuccess,
  });
  return (
    <Modal open={open} onClose={onClose} title="Suspend tenant">
      <form onSubmit={(e) => { e.preventDefault(); m.mutate(); }} className="space-y-3">
        {m.isError && <ErrorBanner error={m.error} />}
        <p className="text-sm text-slate-600">
          Suspending blocks all write operations for this school. Read access is preserved. Use this for non-payment or
          policy violations.
        </p>
        <Input label="Reason (required, shown in audit log)" value={reason} required onChange={(e) => setReason(e.target.value)} />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={m.isPending}>Cancel</Button>
          <Button variant="danger" type="submit" disabled={m.isPending || !reason}>
            {m.isPending ? 'Suspending…' : 'Suspend'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
