'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/auth/AuthProvider';

/**
 * Root redirect — sends authenticated users to their tenant dashboard, anonymous users to
 * the login page. Lives at "/" so a bookmark of the bare origin still does the right thing.
 */
export default function Home() {
  const { state } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (state.status === 'loading') return;
    if (state.status === 'authenticated') {
      router.replace(`/tenants/${state.claims.tenantId}/dashboard`);
    } else {
      router.replace('/login');
    }
  }, [state, router]);

  return (
    <div className="flex items-center justify-center h-screen text-slate-500">
      Loading…
    </div>
  );
}
