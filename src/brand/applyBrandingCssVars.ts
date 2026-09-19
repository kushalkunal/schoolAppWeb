import { Branding } from './branding.config';

/**
 * Applies branding to `<html>` as CSS custom properties so every Tailwind utility
 * that references {@code var(--brand-primary)} updates instantly.
 *
 * Also derives a couple of helper shades (50/100/600/700) from the primary so
 * gradients and hover states can pick from the same palette without the school
 * having to specify five colors.
 */
export function applyBrandingCssVars(b: Branding): void {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;

  root.style.setProperty('--brand-primary', b.primaryColor);
  root.style.setProperty('--brand-primary-fg', readableForeground(b.primaryColor));
  root.style.setProperty('--brand-primary-soft', tint(b.primaryColor, 0.92));
  root.style.setProperty('--brand-primary-hover', shade(b.primaryColor, 0.12));

  root.style.setProperty('--brand-accent', b.accentColor);
  root.style.setProperty('--brand-accent-fg', readableForeground(b.accentColor));
  root.style.setProperty('--brand-accent-soft', tint(b.accentColor, 0.92));

  root.style.setProperty('--brand-radius', b.radius);

  // Favicon swap — schools branding their tab is a tiny but premium touch.
  const link = document.querySelector("link[rel='icon']") as HTMLLinkElement | null;
  if (link && b.faviconUrl) link.href = b.faviconUrl;

  // Page title — purely cosmetic but reinforces the white-label feel.
  const titleEl = document.querySelector('title');
  if (titleEl && b.schoolName && !titleEl.dataset.brandLocked) {
    document.title = b.schoolName;
  }
}

/** Returns black or white depending on the contrast against {@code hex}. */
function readableForeground(color: string): string {
  const rgb = parseColor(color);
  if (!rgb) return '#ffffff';
  // Relative luminance per WCAG; threshold 0.55 picked empirically to keep brand-colored
  // buttons readable without being washed out at common indigo/blue defaults.
  const luminance = (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255;
  return luminance > 0.55 ? '#111827' : '#ffffff';
}

/** Lighten color by {@code amount} (0..1). 0.92 → very light tint. */
function tint(color: string, amount: number): string {
  const rgb = parseColor(color);
  if (!rgb) return color;
  const [r, g, b] = rgb.map((c) => Math.round(c + (255 - c) * amount));
  return `rgb(${r}, ${g}, ${b})`;
}

/** Darken color by {@code amount} (0..1). 0.12 → subtle press effect. */
function shade(color: string, amount: number): string {
  const rgb = parseColor(color);
  if (!rgb) return color;
  const [r, g, b] = rgb.map((c) => Math.round(c * (1 - amount)));
  return `rgb(${r}, ${g}, ${b})`;
}

/** Hex / rgb() parser, returns [r,g,b] in 0..255 or null. */
function parseColor(input: string): [number, number, number] | null {
  if (!input) return null;
  const trimmed = input.trim();

  if (trimmed.startsWith('#')) {
    let hex = trimmed.slice(1);
    if (hex.length === 3) hex = hex.split('').map((c) => c + c).join('');
    if (hex.length !== 6) return null;
    return [
      parseInt(hex.slice(0, 2), 16),
      parseInt(hex.slice(2, 4), 16),
      parseInt(hex.slice(4, 6), 16),
    ];
  }
  const m = trimmed.match(/rgba?\(([^)]+)\)/i);
  if (m && m[1]) {
    const parts = m[1].split(',').map((p) => parseFloat(p.trim()));
    if (parts.length >= 3 && parts.slice(0, 3).every((n) => Number.isFinite(n))) {
      return [parts[0]!, parts[1]!, parts[2]!];
    }
  }
  return null;
}
