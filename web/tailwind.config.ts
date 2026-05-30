import type { Config } from 'tailwindcss';

/**
 * Token-driven Tailwind config. Brand colors flow from CSS custom properties
 * (--brand-primary, --brand-accent, …) so a per-school deploy with a different
 * `NEXT_PUBLIC_BRAND_PRIMARY` rebuilds the same JS bundle but renders with the
 * new palette — and the `BrandingProvider` lets a school override colours at
 * runtime via the platform admin UI without rebuilds.
 *
 * Tailwind's "<token>/<opacity>" syntax (e.g. {@code bg-primary/10}) needs each
 * color expressed as `rgb(R G B / <alpha-value>)`. We therefore can't use plain
 * hex strings from CSS vars; instead each brand color lives in a CSS variable
 * AND has an explicit `<color>-soft` variant rendered by applyBrandingCssVars.
 */
const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Brand-driven (CSS-var-backed). Used by Button/Card/etc.
        primary: {
          DEFAULT: 'var(--brand-primary)',
          fg:      'var(--brand-primary-fg)',
          soft:    'var(--brand-primary-soft)',
          hover:   'var(--brand-primary-hover)',
        },
        accent: {
          DEFAULT: 'var(--brand-accent)',
          fg:      'var(--brand-accent-fg)',
          soft:    'var(--brand-accent-soft)',
        },
        // System colors — fixed; not branded.
        danger:  { DEFAULT: '#DC2626', soft: '#FEF2F2' },
        warning: { DEFAULT: '#D97706', soft: '#FFFBEB' },
        success: { DEFAULT: '#16A34A', soft: '#F0FDF4' },
        info:    { DEFAULT: '#0EA5E9', soft: '#F0F9FF' },
      },
      borderRadius: {
        // Inherits from --brand-radius so soft-vs-friendly schools just change one var.
        brand: 'var(--brand-radius)',
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      boxShadow: {
        // "Lift" — used on hovered Cards. Premium-feeling without being gaudy.
        lift: '0 12px 32px -12px rgb(0 0 0 / 0.18), 0 4px 8px -4px rgb(0 0 0 / 0.06)',
        // "Glow" — focus rings on primary CTAs.
        'brand-glow': '0 0 0 4px color-mix(in srgb, var(--brand-primary) 18%, transparent)',
      },
      animation: {
        'fade-in': 'fadeIn 220ms ease-out',
        'slide-up': 'slideUp 280ms cubic-bezier(0.16, 1, 0.3, 1)',
        shimmer: 'shimmer 1.6s linear infinite',
      },
      keyframes: {
        fadeIn:  { '0%': { opacity: '0' }, '100%': { opacity: '1' } },
        slideUp: {
          '0%': { opacity: '0', transform: 'translateY(8px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        shimmer: {
          '0%':   { backgroundPosition: '-1000px 0' },
          '100%': { backgroundPosition: '1000px 0' },
        },
      },
      // Safe-area-inset utilities — critical for iPhone notch / home bar
      spacing: {
        'safe-top':    'env(safe-area-inset-top)',
        'safe-bottom': 'env(safe-area-inset-bottom)',
        'safe-left':   'env(safe-area-inset-left)',
        'safe-right':  'env(safe-area-inset-right)',
      },
      height: {
        // dvh = dynamic viewport height — fixes 100vh jump on iOS when address bar shows/hides
        dvh: '100dvh',
        'screen-dvh': '100dvh',
      },
      minHeight: {
        dvh: '100dvh',
      },
      backgroundImage: {
        'brand-gradient':
          'linear-gradient(135deg, var(--brand-primary), color-mix(in srgb, var(--brand-primary) 60%, var(--brand-accent)))',
        'brand-radial':
          'radial-gradient(circle at 30% 20%, color-mix(in srgb, var(--brand-primary) 25%, transparent), transparent 60%), radial-gradient(circle at 70% 80%, color-mix(in srgb, var(--brand-accent) 20%, transparent), transparent 55%)',
      },
    },
  },
  plugins: [],
};
export default config;
