'use client';

import { AlertCircle, RefreshCw } from 'lucide-react';
import { isApiError } from '@/api/errors';

interface Props {
  error: unknown;
  onRetry?: () => void;
}

const FRIENDLY: Record<string, string> = {
  FEATURE_DISABLED: 'This feature is not enabled for your plan. Contact your administrator to upgrade.',
  PLAN_LIMIT_EXCEEDED: 'Your plan limit has been reached. Upgrade to continue.',
  TENANT_SUSPENDED: 'This school\'s subscription is suspended. Read access continues; please contact billing.',
  NETWORK_ERROR: 'We can\'t reach the server. Check your connection and try again.',
  INTERNAL_ERROR: 'Something went wrong on our side. Please try again shortly.',
  RATE_LIMIT_EXCEEDED: 'You\'re going a bit too fast. Please wait a moment.',
};

export function ErrorBanner({ error, onRetry }: Props) {
  const code = isApiError(error) ? error.code : undefined;
  const message = code && FRIENDLY[code]
    ? FRIENDLY[code]
    : isApiError(error)
      ? error.message
      : 'Something went wrong.';

  return (
    <div role="alert" className="bg-red-50 border border-red-200 text-red-700 rounded px-4 py-3 flex gap-3 items-start">
      <AlertCircle size={18} className="shrink-0 mt-0.5" />
      <div className="flex-1 text-sm">
        <p>{message}</p>
        {process.env.NODE_ENV !== 'production' && code && (
          <p className="text-xs opacity-70 mt-1">code: {code}</p>
        )}
      </div>
      {onRetry && (
        <button onClick={onRetry} className="text-sm font-medium flex items-center gap-1 hover:underline">
          <RefreshCw size={14} /> Retry
        </button>
      )}
    </div>
  );
}
