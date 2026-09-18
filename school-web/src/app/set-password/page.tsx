'use client';

/**
 * /set-password — used after login when mustResetPassword=true (teacher first login)
 * or when a user wants to set/change their password.
 */

import { FormEvent, Suspense, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { KeyRound, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/auth/AuthProvider';
import { isApiError } from '@/api/errors';
import { BrandLogo } from '@/brand/BrandLogo';
import { Button } from '@/components/ui/Button';

export default function SetPasswordPage() {
  return (
    <Suspense fallback={<main className="min-h-screen grid place-items-center text-slate-500">Loading…</main>}>
      <SetPasswordInner />
    </Suspense>
  );
}

function SetPasswordInner() {
  const { setPassword } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  const required = searchParams.get('required') === 'true';
  const redirectTo = searchParams.get('redirect') ?? '/';

  const [password, setPasswordVal] = useState('');
  const [confirm, setConfirm] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mismatch = confirm.length > 0 && password !== confirm;
  const tooShort = password.length > 0 && password.length < 8;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      setError('Passwords do not match');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await setPassword(password);
      router.replace(redirectTo);
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Could not set password');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="min-h-screen grid place-items-center bg-slate-50 px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex justify-center">
          <BrandLogo withWordmark />
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-8">
          <div className="inline-flex items-center gap-2 text-xs font-medium text-primary bg-primary-soft px-2.5 py-1 rounded-full mb-4">
            <ShieldCheck size={12} />
            {required ? 'Action required' : 'Change password'}
          </div>

          <h1 className="text-xl font-semibold tracking-tight mb-1">
            {required ? 'Set your password' : 'Change password'}
          </h1>
          <p className="text-sm text-slate-500 mb-5">
            {required
              ? 'You were given a temporary password. Please set a new one before continuing.'
              : 'Choose a strong password (at least 8 characters).'}
          </p>

          <form onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="text-sm text-red-600 bg-red-50 border border-red-200 rounded px-3 py-2">
                {error}
              </div>
            )}

            <div>
              <label className="text-sm font-medium text-slate-700 mb-1 block">
                New password
              </label>
              <input
                type="password"
                className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary"
                value={password}
                onChange={(e) => setPasswordVal(e.target.value)}
                placeholder="Min 8 characters"
                required
                minLength={8}
                autoComplete="new-password"
              />
              {tooShort && (
                <p className="text-xs text-red-500 mt-1">At least 8 characters required</p>
              )}
            </div>

            <div>
              <label className="text-sm font-medium text-slate-700 mb-1 block">
                Confirm password
              </label>
              <input
                type="password"
                className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                placeholder="Re-enter new password"
                required
                autoComplete="new-password"
              />
              {mismatch && (
                <p className="text-xs text-red-500 mt-1">Passwords don&apos;t match</p>
              )}
            </div>

            <Button
              type="submit"
              className="w-full justify-center"
              disabled={loading || !!mismatch || tooShort || !password || !confirm}
            >
              {loading ? (
                'Saving…'
              ) : (
                <><KeyRound size={14} className="mr-1.5" /> Set password & continue</>
              )}
            </Button>
          </form>
        </div>
      </div>
    </main>
  );
}
