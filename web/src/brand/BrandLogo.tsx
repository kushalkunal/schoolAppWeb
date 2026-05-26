'use client';

import { useBranding } from './BrandingProvider';
import { cn } from '@/lib/utils';

interface BrandLogoProps {
  /** Render the dark variant when sitting on a coloured background. */
  variant?: 'light' | 'dark';
  /** Show school name next to the icon. */
  withWordmark?: boolean;
  className?: string;
  imgClassName?: string;
}

/**
 * Branded logo. Reads from {@link useBranding} so every render uses the live config.
 * Used in the AppShell sidebar, login screen, and (via DocumentService) on PDFs.
 */
export function BrandLogo({ variant = 'light', withWordmark, className, imgClassName }: BrandLogoProps) {
  const b = useBranding();
  const src = variant === 'dark' && b.logoDarkUrl ? b.logoDarkUrl : b.logoUrl;
  return (
    <div className={cn('inline-flex items-center gap-2', className)}>
      {src ? (
        <img
          src={src}
          alt={`${b.schoolName} logo`}
          className={cn('h-8 w-auto object-contain', variant === 'light' ? 'text-primary' : 'text-white', imgClassName)}
        />
      ) : (
        <div className={cn(
          'h-8 w-8 grid place-items-center rounded-brand font-bold text-sm',
          variant === 'light' ? 'bg-primary text-primary-fg' : 'bg-white text-primary',
        )}>
          {b.shortName.charAt(0).toUpperCase()}
        </div>
      )}
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
