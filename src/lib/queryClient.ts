import { QueryClient } from '@tanstack/react-query';
import { ApiError, isApiError } from '@/api/errors';

/**
 * Per docs/frontend/09-data-fetching-patterns.md §3:
 *  - retry only on transient codes (network / 5xx / 429)
 *  - exponential backoff to 8s cap
 *  - 30-second default staleTime — per-query overrides bump where needed
 *  - mutations never auto-retry
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        if (!isApiError(error)) return failureCount < 3;
        return (error as ApiError).isTransient && failureCount < 3;
      },
      retryDelay: (attempt) => Math.min(1000 * Math.pow(2, attempt), 8_000),
      staleTime: 30_000,
      refetchOnWindowFocus: process.env.NODE_ENV === 'production',
    },
    mutations: {
      retry: false,
    },
  },
});
