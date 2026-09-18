import { HTMLAttributes, forwardRef } from 'react';
import { cn } from '@/lib/utils';

type Tone = 'default' | 'subtle' | 'gradient' | 'flat';
type Padding = 'none' | 'sm' | 'md' | 'lg';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  tone?: Tone;
  padding?: Padding;
  /** Adds a hover-lift effect (use for clickable cards in lists). */
  interactive?: boolean;
}

/**
 * Premium Card surface.
 *
 * - `default`  — white, subtle shadow, soft border. The workhorse.
 * - `subtle`   — slate-50 tint; for grouped/secondary content.
 * - `gradient` — branded background; used at most once per page (welcome / hero).
 * - `flat`     — no shadow, no border; for nested cards.
 */
export const Card = forwardRef<HTMLDivElement, CardProps>(function Card(
  { className, tone = 'default', padding = 'md', interactive, children, ...props },
  ref,
) {
  const tones: Record<Tone, string> = {
    default:  'bg-white border border-slate-200/80 shadow-sm',
    subtle:   'bg-slate-50 border border-slate-200/60',
    gradient: 'bg-brand-gradient text-primary-fg border border-white/10 shadow-md',
    flat:     'bg-white',
  };
  const paddings: Record<Padding, string> = {
    none: 'p-0',
    sm:   'p-3',
    md:   'p-5',
    lg:   'p-7',
  };
  return (
    <div
      ref={ref}
      className={cn(
        'rounded-brand',
        tones[tone],
        paddings[padding],
        interactive && 'transition-all hover:shadow-lift hover:-translate-y-px cursor-pointer',
        className,
      )}
      {...props}
    >
      {children}
    </div>
  );
});

export function CardHeader({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('px-5 py-4 border-b border-slate-200/70', className)} {...props} />;
}

export function CardBody({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('p-5', className)} {...props} />;
}

export function CardFooter({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('px-5 py-3 border-t border-slate-200/70 bg-slate-50/50 rounded-b-brand', className)} {...props} />;
}

export function CardTitle({ className, ...props }: HTMLAttributes<HTMLHeadingElement>) {
  return <h3 className={cn('text-base font-semibold text-slate-900', className)} {...props} />;
}

export function CardDescription({ className, ...props }: HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn('text-sm text-slate-500 mt-0.5', className)} {...props} />;
}
