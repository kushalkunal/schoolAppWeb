'use client';

import { useBranding } from './BrandingProvider';
import { DEFAULT_BRANDING } from './branding.config';
import { cn } from '@/lib/utils';

interface BrandLogoProps {
  /** Render the dark variant when sitting on a coloured background. */
  variant?: 'light' | 'dark';
  /** Show school name next to the icon. */
  withWordmark?: boolean;
  /**
   * Suppress the platform (Scalio) default logo when the school hasn't uploaded its own.
   * Used in the post-login app shell so a school's screen shows the school's logo (if any)
   * and otherwise just the name — never the Scalio mark. Login/footer keep the default.
   */
  hideDefaultLogo?: boolean;
  className?: string;
  imgClassName?: string;
}

/**
 * Branded logo. Reads from {@link useBranding} so every render uses the live config.
 * Used in the AppShell sidebar, login screen, and (via DocumentService) on PDFs.
 */
export function BrandLogo({ variant = 'light', withWordmark, hideDefaultLogo, className, imgClassName }: BrandLogoProps) {
  const b = useBranding();
  const rawSrc = variant === 'dark' && b.logoDarkUrl ? b.logoDarkUrl : b.logoUrl;
  // A school is "using its own logo" only when its logo differs from the platform default.
  const isPlatformDefault = !rawSrc || rawSrc === DEFAULT_BRANDING.logoUrl;
  // When hideDefaultLogo is set and the school has no logo of its own, show no icon at all.
  const src = hideDefaultLogo && isPlatformDefault ? '' : rawSrc;
  return (
    <div className={cn('inline-flex items-center gap-2', className)}>
      {src ? (
        <img
          src={src}
          alt={`${b.schoolName} logo`}
          className={cn('h-8 w-auto object-contain', variant === 'light' ? 'text-primary' : 'text-white', imgClassName)}
        />
      ) : !hideDefaultLogo ? (
        <div className={cn(
          'h-8 w-8 grid place-items-center rounded-brand font-bold text-sm',
          variant === 'light' ? 'bg-primary text-primary-fg' : 'bg-white text-primary',
        )}>
          {b.shortName.charAt(0).toUpperCase()}
        </div>
      ) : null}
      {withWordmark && (
        <span className={cn(
          'font-semibold tracking-tight',
          variant === 'light' ? 'text-slate-900' : 'text-white',
        )}>
          {b.shortName}
        </span>
      )}
    </div>
  );
}
