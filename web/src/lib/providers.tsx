'use client';

import { QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/auth/AuthProvider';
import { BrandingProvider } from '@/brand/BrandingProvider';
import { ToastProvider } from '@/components/ui/Toast';
import { queryClient } from './queryClient';

/**
 * Single client-side providers wrapper. The root server-rendered layout includes this once
 * so AuthProvider's localStorage reads + React Query + per-school branding are usable
 * everywhere downstream.
 *
 * <p>Provider order matters:
 * <ul>
 *   <li>QueryClient at the top — React Query hooks usable anywhere.</li>
 *   <li>BrandingProvider next — CSS vars applied before any styled component renders.</li>
 *   <li>AuthProvider inside — once a JWT is read, child components can call useAuth
 *       safely; the tenant layout passes the JWT's schoolId down for runtime branding
 *       overrides.</li>
 * </ul>
 */
export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <BrandingProvider>
        <ToastProvider>
          <AuthProvider>{children}</AuthProvider>
        </ToastProvider>
      </BrandingProvider>
    </QueryClientProvider>
  );
}
