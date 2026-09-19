'use client';

/**
 * Branding admin — Slice 32.
 *
 * Lets a school OWNER/ADMIN edit the per-school white-label override stored in
 * {@code schools.settings.branding} JSONB. The form streams every change into the active
 * {@link useBrandingOverride} so the rest of the app re-themes live — sidebar, header,
 * buttons, focus rings — without a reload. Saving persists to the backend and the next
 * mount picks the value up via the public branding endpoint.
 */

import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { Palette, Save, RotateCcw } from 'lucide-react';
import { brandingApi, type BrandingResponse } from '@/api/endpoints/branding';
import { useBranding, useBrandingOverride } from '@/brand/BrandingProvider';
import { PageHeader } from '@/components/ui/PageHeader';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { ErrorBanner } from '@/components/ui/ErrorBanner';
import { Spinner } from '@/components/ui/Spinner';
import { useToast } from '@/components/ui/Toast';
import { Badge } from '@/components/ui/Badge';
import { OWNER_OR_ADMIN, RequireRole, useHasRole } from '@/auth/RequireRole';

const BLANK: BrandingResponse = {
  schoolName: '', shortName: '', tagline: '', affiliation: '',
  logoUrl: '', logoDarkUrl: '', faviconUrl: '', loginHeroUrl: '',
  primaryColor: '', accentColor: '', radius: '',
  contactPhone: '', contactEmail: '', address: '', websiteUrl: '',
  gstin: '',
  socialFacebook: '', socialInstagram: '', socialYoutube: '', socialX: '',
  signatureUrl: '', signatoryName: '', signatoryTitle: '',
};

export default function BrandingPage() {
  return (
    <RequireRole roles={OWNER_OR_ADMIN}>
      <BrandingInner />
    </RequireRole>
  );
}

function BrandingInner() {
  const params = useParams();
  const tenantId = typeof params.tenantId === 'string' ? params.tenantId : '';
  const qc = useQueryClient();
  const { success, error: toastError } = useToast();
  const canEdit = useHasRole(...OWNER_OR_ADMIN);
  const liveBranding = useBranding();
  const overrideBranding = useBrandingOverride();

  const q = useQuery({
    queryKey: ['branding-admin', tenantId],
    queryFn: () => brandingApi.getPublic(tenantId),
    enabled: !!tenantId,
  });

  const [form, setForm] = useState<BrandingResponse>(BLANK);
  const [serverSnapshot, setServerSnapshot] = useState<BrandingResponse>(BLANK);

  // Seed once when data arrives.
  useEffect(() => {
    if (q.data) {
      const seeded: BrandingResponse = { ...BLANK, ...q.data };
      setForm(seeded);
      setServerSnapshot(seeded);
    }
  }, [q.data]);

  const save = useMutation({
    mutationFn: (req: BrandingResponse) => brandingApi.update(tenantId, req),
    onSuccess: (saved) => {
      const next: BrandingResponse = { ...BLANK, ...saved };
      setForm(next);
      setServerSnapshot(next);
      qc.invalidateQueries({ queryKey: ['branding-admin', tenantId] });
      success('Branding saved');
    },
    onError: (e: Error) => toastError(e.message || 'Save failed'),
  });

  // Push changes into the live BrandingProvider so the rest of the app re-themes.
  function patch<K extends keyof BrandingResponse>(key: K, value: BrandingResponse[K]) {
    setForm((f) => ({ ...f, [key]: value }));
    // Only push fields that map to the runtime Branding shape (string-typed everywhere).
    overrideBranding({ [key]: (value ?? '') as string });
  }

  function revertToServer() {
    setForm(serverSnapshot);
    overrideBranding({
      schoolName: serverSnapshot.schoolName ?? '',
      shortName: serverSnapshot.shortName ?? '',
      tagline: serverSnapshot.tagline ?? '',
      affiliation: serverSnapshot.affiliation ?? '',
      logoUrl: serverSnapshot.logoUrl ?? '',
      logoDarkUrl: serverSnapshot.logoDarkUrl ?? '',
      faviconUrl: serverSnapshot.faviconUrl ?? '',
      loginHeroUrl: serverSnapshot.loginHeroUrl ?? '',
      primaryColor: serverSnapshot.primaryColor ?? '',
      accentColor: serverSnapshot.accentColor ?? '',
      radius: serverSnapshot.radius ?? '',
      contactPhone: serverSnapshot.contactPhone ?? '',
      contactEmail: serverSnapshot.contactEmail ?? '',
      address: serverSnapshot.address ?? '',
      websiteUrl: serverSnapshot.websiteUrl ?? '',
      gstin: serverSnapshot.gstin ?? '',
      socialFacebook: serverSnapshot.socialFacebook ?? '',
      socialInstagram: serverSnapshot.socialInstagram ?? '',
      socialYoutube: serverSnapshot.socialYoutube ?? '',
      socialX: serverSnapshot.socialX ?? '',
    });
  }

  if (q.isLoading) return <div className="flex items-center gap-2 text-slate-500"><Spinner /> Loading…</div>;
  if (q.isError)   return <ErrorBanner error={q.error} onRetry={() => q.refetch()} />;

  const dirty = JSON.stringify(form) !== JSON.stringify(serverSnapshot);

  return (
    <div className="space-y-4 max-w-5xl">
      <PageHeader
        icon={<Palette size={18} />}
        title="Branding"
        description="Logo, colors, contact info. Changes apply live across the app; PDFs (receipts, TCs, hall tickets) pick them up on next render."
        actions={
          <div className="flex items-center gap-2">
            {dirty && <Badge tone="warning" size="sm">Unsaved</Badge>}
            <Button variant="ghost" onClick={revertToServer} disabled={!dirty || save.isPending}>
              <RotateCcw size={14} className="mr-1" /> Revert
            </Button>
            <Button
              onClick={() => save.mutate(form)}
              disabled={!dirty || save.isPending || !canEdit}
            >
              <Save size={14} className="mr-1" />
              {save.isPending ? 'Saving…' : 'Save'}
            </Button>
          </div>
        }
      />

      <div className="grid grid-cols-12 gap-4">
        <div className="col-span-12 lg:col-span-8 space-y-4">
          {/* Identity */}
          <Card>
            <CardHeader><CardTitle>Identity</CardTitle></CardHeader>
            <CardBody className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input label="School name (full)" value={form.schoolName ?? ''}
                onChange={(e) => patch('schoolName', e.target.value)} />
              <Input label="Short name" hint="Shown where space is tight" value={form.shortName ?? ''}
                onChange={(e) => patch('shortName', e.target.value)} />
              <Input label="Tagline" value={form.tagline ?? ''}
                onChange={(e) => patch('tagline', e.target.value)}
                className="sm:col-span-2" />
              <Input label="Board affiliation" placeholder="Affiliated to CBSE"
                value={form.affiliation ?? ''}
                onChange={(e) => patch('affiliation', e.target.value)}
                className="sm:col-span-2" />
            </CardBody>
          </Card>

          {/* Imagery */}
          <Card>
            <CardHeader><CardTitle>Logo & imagery</CardTitle></CardHeader>
            <CardBody className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input label="Logo URL"
                hint="Public PNG/SVG/WebP — square works best"
                value={form.logoUrl ?? ''}
                onChange={(e) => patch('logoUrl', e.target.value)} />
              <Input label="Dark-mode logo URL"
                hint="Optional; falls back to the main logo"
                value={form.logoDarkUrl ?? ''}
                onChange={(e) => patch('logoDarkUrl', e.target.value)} />
              <Input label="Favicon URL" value={form.faviconUrl ?? ''}
                onChange={(e) => patch('faviconUrl', e.target.value)} />
              <Input label="Login hero image URL"
                hint="Optional background on the sign-in page"
                value={form.loginHeroUrl ?? ''}
                onChange={(e) => patch('loginHeroUrl', e.target.value)} />
            </CardBody>
          </Card>

          {/* Colors */}
          <Card>
            <CardHeader><CardTitle>Colors & radius</CardTitle></CardHeader>
            <CardBody className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <ColorInput label="Primary"
                value={form.primaryColor ?? ''}
                onChange={(v) => patch('primaryColor', v)} />
              <ColorInput label="Accent"
                value={form.accentColor ?? ''}
                onChange={(v) => patch('accentColor', v)} />
              <Input label="Border radius"
                hint="CSS value — try 0.5rem (soft) or 1rem (friendly)"
                value={form.radius ?? ''}
                onChange={(e) => patch('radius', e.target.value)} />
            </CardBody>
          </Card>

          {/* Contact */}
          <Card>
            <CardHeader><CardTitle>Contact & footer</CardTitle></CardHeader>
            <CardBody className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input label="Phone" value={form.contactPhone ?? ''}
                onChange={(e) => patch('contactPhone', e.target.value)} />
              <Input label="Email" value={form.contactEmail ?? ''}
                onChange={(e) => patch('contactEmail', e.target.value)} />
              <Input label="Website" value={form.websiteUrl ?? ''}
                onChange={(e) => patch('websiteUrl', e.target.value)} />
              <Input label="GSTIN"
                hint="Printed on fee receipts when present"
                value={form.gstin ?? ''}
                onChange={(e) => patch('gstin', e.target.value)} />
              <div className="sm:col-span-2">
                <label className="block text-sm text-slate-600 mb-1">Address</label>
                <textarea
                  className="w-full border border-slate-200 rounded px-3 py-2 text-sm"
                  rows={3}
                  value={form.address ?? ''}
                  onChange={(e) => patch('address', e.target.value)}
                />
              </div>
            </CardBody>
          </Card>

          {/* Documents & signature — drives admit cards, report cards and fee receipts */}
          <Card>
            <CardHeader>
              <CardTitle>Documents & signature</CardTitle>
              <p className="text-xs text-slate-500 mt-1">
                The logo, primary colour and details above already brand your admit cards,
                report cards and fee receipts. Add an authorising signature below — it prints
                above the “Principal” line, alongside a QR code that lets anyone verify the
                document is genuine.
              </p>
            </CardHeader>
            <CardBody className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input label="Signature image URL"
                hint="PNG/JPG of the principal's signature (transparent background looks best)"
                value={form.signatureUrl ?? ''}
                onChange={(e) => patch('signatureUrl', e.target.value)} />
              <Input label="Signatory name"
                hint="Defaults to “Principal” if left blank"
                value={form.signatoryName ?? ''}
                onChange={(e) => patch('signatoryName', e.target.value)} />
              <Input label="Signatory title"
                placeholder="Principal"
                value={form.signatoryTitle ?? ''}
                onChange={(e) => patch('signatoryTitle', e.target.value)} />
            </CardBody>
          </Card>

          {/* Social */}
          <Card>
            <CardHeader><CardTitle>Social links</CardTitle></CardHeader>
            <CardBody className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input label="Facebook URL" value={form.socialFacebook ?? ''}
                onChange={(e) => patch('socialFacebook', e.target.value)} />
              <Input label="Instagram URL" value={form.socialInstagram ?? ''}
                onChange={(e) => patch('socialInstagram', e.target.value)} />
              <Input label="YouTube URL" value={form.socialYoutube ?? ''}
                onChange={(e) => patch('socialYoutube', e.target.value)} />
              <Input label="X / Twitter URL" value={form.socialX ?? ''}
                onChange={(e) => patch('socialX', e.target.value)} />
            </CardBody>
          </Card>
        </div>

        {/* Live preview */}
        <div className="col-span-12 lg:col-span-4">
          <Card>
            <CardHeader><CardTitle>Live preview</CardTitle></CardHeader>
            <CardBody className="space-y-3">
              <div className="rounded-brand border border-slate-200 overflow-hidden">
                <div className="h-14 px-4 flex items-center gap-2 bg-brand-gradient text-primary-fg">
                  {liveBranding.logoUrl && (
                    /* eslint-disable-next-line @next/next/no-img-element */
                    <img src={liveBranding.logoUrl} alt="" className="h-7 w-7 object-contain bg-white/20 rounded" />
                  )}
                  <span className="font-semibold truncate">{liveBranding.shortName || liveBranding.schoolName || '—'}</span>
                </div>
                <div className="p-3 space-y-2 bg-white">
                  <div className="text-sm font-medium text-slate-900 truncate">{liveBranding.schoolName || '—'}</div>
                  {liveBranding.tagline && (
                    <div className="text-xs text-slate-500 truncate">{liveBranding.tagline}</div>
                  )}
                  {liveBranding.affiliation && (
                    <Badge tone="primary" size="sm">{liveBranding.affiliation}</Badge>
                  )}
                  <div className="flex gap-2 pt-1">
                    <Button>Primary</Button>
                    <Button variant="secondary">Secondary</Button>
                  </div>
                </div>
              </div>
              <div className="text-xs text-slate-500 space-y-1">
                <div>Primary: <code>{liveBranding.primaryColor || '—'}</code></div>
                <div>Accent: <code>{liveBranding.accentColor || '—'}</code></div>
                <div>Radius: <code>{liveBranding.radius || '—'}</code></div>
              </div>
              <p className="text-xs text-slate-400">
                Changes preview instantly across the whole app. Hit Save to make them stick for everyone.
              </p>
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  );
}

/**
 * Color input pair — text field + native swatch. Empty string means "use build-time default".
 */
function ColorInput({
  label, value, onChange,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
}) {
  // Native <input type="color"> requires a 7-char hex; show a fallback if the user pasted
  // something exotic ("hsl(...)" etc.) — the text field stays the source of truth.
  const isHex = /^#[0-9a-fA-F]{6}$/.test(value);
  return (
    <div>
      <label className="block text-sm text-slate-600 mb-1">{label}</label>
      <div className="flex items-center gap-2">
        <input
          type="color"
          className="h-9 w-12 rounded border border-slate-200 bg-white cursor-pointer disabled:opacity-50"
          value={isHex ? value : '#4f46e5'}
          onChange={(e) => onChange(e.target.value)}
          aria-label={`${label} color picker`}
        />
        <input
          className="flex-1 border border-slate-200 rounded px-2 py-1.5 text-sm"
          placeholder="#4f46e5"
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
      </div>
    </div>
  );
}
