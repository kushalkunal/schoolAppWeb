/**
 * Single source of truth for per-school white-label branding.
 *
 * ## Deployment model
 *
 * One backend engine serves every school. Each school gets its own frontend deploy
 * (Vercel project, Netlify site, custom domain) with `NEXT_PUBLIC_BRAND_*` env vars
 * set at build time. The fallback DEFAULTS object below covers the "demo" or
 * "unbranded" deploy.
 *
 * ## Override priority
 *
 * 1. **Runtime fetch** — `/api/v1/public/schools/{schoolId}/branding` (see BrandingProvider).
 *    Lets a principal change colours in the platform admin UI without a rebuild.
 * 2. **Build-time env vars** — baked into the JS at `next build` time.
 * 3. **Defaults** below — last-resort, for the docs/demo deploy.
 *
 * ## What "branding" includes
 *
 * - School name + short name + tagline
 * - Logo (light + dark variants) + favicon
 * - Brand colors (primary, accent) — surfaced as CSS variables
 * - Contact info on receipts / TCs / hall tickets
 * - Board affiliation badge
 * - Optional accent imagery (login background)
 */

export interface Branding {
  /** Full legal name shown on certificates + receipts. */
  schoolName: string;
  /** Short name shown in nav/header where space is tight. */
  shortName: string;
  /** One-line marketing tagline shown on /login. Empty string hides it. */
  tagline: string;
  /** Affiliation badge text — "Affiliated to CBSE", "ICSE", etc. */
  affiliation: string;

  /** URLs (absolute or /public/* path). PNG/SVG/WebP all fine. */
  logoUrl: string;
  /** Logo variant for dark backgrounds (defaults to logoUrl). */
  logoDarkUrl: string;
  /** Favicon — typically a square /public/favicon.svg. */
  faviconUrl: string;
  /** Optional hero image behind /login. Empty = solid gradient. */
  loginHeroUrl: string;

  /** Brand color — primary CTAs, headings, focus rings. Any CSS color. */
  primaryColor: string;
  /** Accent color — secondary CTAs, badges, highlights. */
  accentColor: string;
  /** Border radius design token. "0.5rem" = soft, "1rem" = friendly. */
  radius: string;

  /** Contact info — surfaces on receipts + TCs. */
  contactPhone: string;
  contactEmail: string;
  /** Multi-line address shown at the foot of every printed document. */
  address: string;
  /** Website URL — shown on receipts + TCs. */
  websiteUrl: string;

  /** GSTIN for fee receipts. Empty = no GSTIN line printed. */
  gstin: string;

  /** Social links — surface on login page footer + parent app. */
  socialFacebook: string;
  socialInstagram: string;
  socialYoutube: string;
  socialX: string;
}

const env = (key: string, fallback: string): string =>
  // process.env reads are inlined at build time by Next; runtime users get the value
  // baked in unless replaced by the BrandingProvider runtime fetch.
  (process.env[key] as string | undefined) ?? fallback;

/**
 * Default branding used when no env override is set. Schools point their frontend
 * deploy at this repo, fill in NEXT_PUBLIC_BRAND_* in their .env.production, hit
 * `next build`, and they get their own branded build.
 */
/**
 * The platform (white-label provider) identity. School branding always leads; this
 * Scalio attribution is small and unobtrusive — login footer, system footer, billing,
 * help/about only. Never on documents or in place of a school's own branding.
 */
export const PLATFORM = {
  name: 'ScalioCampus',
  company: 'ScalioLab',
  poweredBy: 'Powered by ScalioLab',
  poweredByUrl: 'https://scaliolab.com/',
} as const;

export const DEFAULT_BRANDING: Branding = {
  schoolName:    env('NEXT_PUBLIC_BRAND_SCHOOL_NAME',  'ScalioCampus'),
  shortName:     env('NEXT_PUBLIC_BRAND_SHORT_NAME',   'ScalioCampus'),
  tagline:       env('NEXT_PUBLIC_BRAND_TAGLINE',      'Modern school operations, simplified.'),
  affiliation:   env('NEXT_PUBLIC_BRAND_AFFILIATION',  ''),

  logoUrl:       env('NEXT_PUBLIC_BRAND_LOGO',         '/brand/logo.svg'),
  logoDarkUrl:   env('NEXT_PUBLIC_BRAND_LOGO_DARK',    ''),
  faviconUrl:    env('NEXT_PUBLIC_BRAND_FAVICON',      '/favicon.svg'),
  loginHeroUrl:  env('NEXT_PUBLIC_BRAND_LOGIN_HERO',   ''),

  primaryColor:  env('NEXT_PUBLIC_BRAND_PRIMARY',      '#B00000'),  // ScalioCampus red
  accentColor:   env('NEXT_PUBLIC_BRAND_ACCENT',       '#111827'),  // ScalioCampus slate
  radius:        env('NEXT_PUBLIC_BRAND_RADIUS',       '0.625rem'),

  contactPhone:  env('NEXT_PUBLIC_BRAND_PHONE',        ''),
  contactEmail:  env('NEXT_PUBLIC_BRAND_EMAIL',        ''),
  address:       env('NEXT_PUBLIC_BRAND_ADDRESS',      ''),
  websiteUrl:    env('NEXT_PUBLIC_BRAND_WEBSITE',      ''),

  gstin:         env('NEXT_PUBLIC_BRAND_GSTIN',        ''),

  socialFacebook:  env('NEXT_PUBLIC_BRAND_FACEBOOK',  ''),
  socialInstagram: env('NEXT_PUBLIC_BRAND_INSTAGRAM', ''),
  socialYoutube:   env('NEXT_PUBLIC_BRAND_YOUTUBE',   ''),
  socialX:         env('NEXT_PUBLIC_BRAND_X',         ''),
};

/**
 * Merges runtime overrides (from /api/v1/public/schools/{id}/branding) onto the
 * build-time defaults. Empty/null overrides fall through to defaults.
 */
export function mergeBranding(base: Branding, override: Partial<Branding> | null | undefined): Branding {
  if (!override) return base;
  const merged: Branding = { ...base };
  for (const k of Object.keys(override) as (keyof Branding)[]) {
    const v = override[k];
    if (v != null && String(v).length > 0) {
      (merged as unknown as Record<string, string>)[k] = String(v);
    }
  }
  return merged;
}
