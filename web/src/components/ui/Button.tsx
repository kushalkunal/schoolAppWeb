'use client';

import { ButtonHTMLAttributes, forwardRef } from 'react';
import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'accent' | 'outline' | 'subtle';
type Size = 'xs' | 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  /** Show a leading spinner; disables interaction. */
  loading?: boolean;
  /** When the button is the primary CTA on a hero/empty-state, this gives it a glow. */
  glow?: boolean;
}

/**
 * Premium Button — token-driven, brand-aware. Variants cover every CTA in the app:
 *
 * - `primary`  — brand-color CTA, used at most once per screen.
 * - `accent`   — secondary brand color, for "Confirm / Done" alongside `secondary`.
 * - `secondary`— white surface with border; quiet companion to primary.
 * - `outline`  — transparent surface with brand-color border; on coloured backgrounds.
 * - `ghost`    — no background; for icon buttons / table actions.
 * - `subtle`   — soft brand tint; "promotional" without being primary.
 * - `danger`   — destructive actions only.
 *
 * Hover/active/focus states use CSS color-mix on the brand vars so every variant
 * looks correct against any school's palette without per-school CSS.
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant = 'primary', size = 'md', disabled, loading, glow, children, type = 'button', ...props },
  ref,
) {
  const base =
    'inline-flex items-center justify-center font-medium rounded-brand transition-all ' +
    'select-none whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 ' +
    'focus-visible:ring-offset-1 focus-visible:ring-offset-white ' +
    'disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0 ' +
    'active:translate-y-px';

  const variants: Record<Variant, string> = {
    primary:
      'bg-primary text-primary-fg shadow-sm hover:shadow-md hover:-translate-y-px ' +
      'hover:bg-primary-hover focus-visible:ring-primary',
    accent:
      'bg-accent text-accent-fg shadow-sm hover:shadow-md hover:-translate-y-px focus-visible:ring-accent',
    secondary:
      'bg-white text-slate-800 border border-slate-200 shadow-sm ' +
      'hover:bg-slate-50 hover:border-slate-300 focus-visible:ring-slate-400',
    outline:
      'bg-transparent text-primary border border-primary/40 hover:bg-primary-soft ' +
      'hover:border-primary focus-visible:ring-primary',
    ghost:
      'text-slate-700 hover:bg-slate-100 hover:text-slate-900 focus-visible:ring-slate-300',
    subtle:
      'bg-primary-soft text-primary hover:bg-primary-soft/70 focus-visible:ring-primary',
    danger:
      'bg-danger text-white shadow-sm hover:shadow-md hover:bg-red-700 focus-visible:ring-danger',
  };

  const sizes: Record<Size, string> = {
    xs: 'h-7  px-2.5 text-xs gap-1',
    sm: 'h-8  px-3   text-xs gap-1.5',
    md: 'h-10 px-4   text-sm gap-2',
    lg: 'h-12 px-6   text-base gap-2',
  };

  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      className={cn(
        base,
        variants[variant],
        sizes[size],
        glow && variant === 'primary' && 'shadow-brand-glow',
        className,
      )}
      {...props}
    >
      {loading && <Loader2 className="animate-spin" size={size === 'xs' ? 12 : size === 'sm' ? 14 : 16} />}
      {children}
    </button>
  );
});
