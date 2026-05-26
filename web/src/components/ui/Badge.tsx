import { HTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

type Tone = 'neutral' | 'primary' | 'accent' | 'success' | 'warning' | 'danger' | 'info';
type Size = 'sm' | 'md';

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: Tone;
  size?: Size;
  /** Adds a dot indicator on the left (good for status pills). */
  dot?: boolean;
}

/** Compact status pill — used in tables, stat tiles, list rows. */
export function Badge({ className, tone = 'neutral', size = 'sm', dot, children, ...props }: BadgeProps) {
  const tones: Record<Tone, string> = {
    neutral: 'bg-slate-100   text-slate-700',
    primary: 'bg-primary-soft text-primary',
    accent:  'bg-accent-soft  text-accent',
    success: 'bg-success/10  text-success',
    warning: 'bg-warning/10  text-warning',
    danger:  'bg-danger/10   text-danger',
    info:    'bg-info/10     text-info',
  };
  const dotTones: Record<Tone, string> = {
    neutral: 'bg-slate-500',
    primary: 'bg-primary',
    accent:  'bg-accent',
    success: 'bg-success',
    warning: 'bg-warning',
    danger:  'bg-danger',
    info:    'bg-info',
  };
  const sizes: Record<Size, string> = {
    sm: 'h-5 px-2 text-[11px] gap-1',
    md: 'h-6 px-2.5 text-xs gap-1.5',
  };
  return (
    <span
      className={cn(
        'inline-flex items-center font-medium rounded-full',
        tones[tone],
        sizes[size],
        className,
      )}
      {...props}
    >
      {dot && <span className={cn('w-1.5 h-1.5 rounded-full', dotTones[tone])} />}
      {children}
    </span>
  );
}
