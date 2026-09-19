'use client';

import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { Branding, DEFAULT_BRANDING, mergeBranding } from './branding.config';
import { applyBrandingCssVars } from './applyBrandingCssVars';

interface BrandingState {
  branding: Branding;
  /** Lets nested components (or future settings page) swap branding live. */
  override: (patch: Partial<Branding>) => void;
}

const Ctx = createContext<BrandingState | null>(null);

/**
 * Sits below QueryClientProvider in src/lib/providers.tsx. Two responsibilities:
 *
 * 1. **Apply CSS variables** on mount — every Tailwind utility that consumes
 *    `var(--brand-primary)` etc. picks up the school's palette without rebuilds.
 * 2. **Fetch runtime overrides** (logo + colors stored in schools.settings JSONB)
 *    asynchronously once the JWT is available. Build-time defaults render
 *    immediately so there's never a flash of unstyled content.
 *
 * Runtime fetch is silent on failure — we always have build-time defaults to fall
 * back to. Schools that don't want a runtime override (eg. fully static deploy)
 * simply never set a row in the settings JSONB.
 */
export function BrandingProvider({
  initialBranding = DEFAULT_BRANDING,
  schoolId,
  children,
}: {
  initialBranding?: Branding;
  /** When provided, the provider fetches per-school overrides at mount. */
  schoolId?: string;
  children: React.ReactNode;
}) {
  const [branding, setBranding] = useState<Branding>(initialBranding);

  // Reflect every change into <html> CSS vars + favicon.
  useEffect(() => { applyBrandingCssVars(branding); }, [branding]);

  // Optional runtime fetch.
  useEffect(() => {
    if (!schoolId) return;
    let cancelled = false;
    const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? '';
    fetch(`${base}/api/v1/public/schools/${schoolId}/branding`)
      .then((r) => r.ok ? r.json() : null)
      .then((envelope) => {
        if (cancelled || !envelope?.data) return;
        setBranding((current) => mergeBranding(current, envelope.data));
      })
      .catch(() => {
        // Silent fallback to build-time defaults — never blocks the UI.
      });
    return () => { cancelled = true; };
  }, [schoolId]);

  const value = useMemo<BrandingState>(() => ({
    branding,
    override: (patch) => setBranding((b) => mergeBranding(b, patch)),
  }), [branding]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useBranding(): Branding {
  const ctx = useContext(Ctx);
  return ctx?.branding ?? DEFAULT_BRANDING;
}

export function useBrandingOverride(): (patch: Partial<Branding>) => void {
  const ctx = useContext(Ctx);
  return ctx?.override ?? (() => {});
}
