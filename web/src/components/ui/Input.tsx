import { InputHTMLAttributes, forwardRef } from 'react';
import { cn } from '@/lib/utils';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  hint?: string;
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, hint, error, className, ...props },
  ref,
) {
  return (
    <label className="block">
      {label && <span className="text-sm text-slate-700 mb-1 inline-block">{label}</span>}
      <input
        ref={ref}
        className={cn(
          'block w-full rounded border px-3 py-2 text-sm',
          error ? 'border-danger' : 'border-slate-300',
          'focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary',
          className,
        )}
        {...props}
      />
      {hint && !error && <span className="text-xs text-slate-500 mt-1 inline-block">{hint}</span>}
      {error && <span className="text-xs text-danger mt-1 inline-block">{error}</span>}
    </label>
  );
});
