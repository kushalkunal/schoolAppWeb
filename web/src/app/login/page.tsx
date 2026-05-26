'use client';

/**
 * Premium login. Slice 35 — two methods (Password / OTP), two identifiers (Phone / Email).
 *
 * - Default method: Password. The OTP path is always available as a fallback (and is the
 *   only way for a fresh account that hasn't set a password yet).
 * - On any sign-in the redirect target is sanitised so a malicious `?redirect=...` can't
 *   bounce the user into a different tenant.
 */

import { FormEvent, Suspense, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { ShieldCheck, Mail, Phone, ArrowLeft, KeyRound, Smartphone, Facebook, Instagram, Youtube } from 'lucide-react';
import { useAuth, SendOtpInput, VerifyOtpInput, PasswordLoginInput } from '@/auth/AuthProvider';
import { isApiError } from '@/api/errors';
import { useBranding } from '@/brand/BrandingProvider';
import { BrandLogo } from '@/brand/BrandLogo';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

type Channel = 'PHONE' | 'EMAIL' | 'BOTH';
const SIGNUP_CHANNEL = (process.env.NEXT_PUBLIC_SIGNUP_CHANNEL ?? 'BOTH') as Channel;

export default function LoginPage() {
  return (
    <Suspense fallback={<main className="min-h-screen grid place-items-center text-slate-500">Loading…</main>}>
      <LoginInner />
    </Suspense>
  );
}

function LoginInner() {
  const b = useBranding();
  const { sendOtp, loginWithOtp, loginWithPassword } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  const [method, setMethod] = useState<'PASSWORD' | 'OTP'>('PASSWORD');
  const [step, setStep] = useState<'identifier' | 'otp'>('identifier');
  const [channel, setChannel] = useState<'PHONE' | 'EMAIL'>(
    SIGNUP_CHANNEL === 'EMAIL' ? 'EMAIL' : 'PHONE'
  );
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [otp, setOtp] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const idPayload = (): SendOtpInput =>
    channel === 'PHONE' ? { phone: identifier } : { email: identifier };

  async function onPasswordSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const input: PasswordLoginInput = { ...idPayload(), password };
      const claims = await loginWithPassword(input);
      router.replace(sanitizeRedirect(searchParams.get('redirect'), claims.tenantId));
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Sign-in failed');
    } finally {
      setLoading(false);
    }
  }

  async function onSendOtp(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await sendOtp(idPayload());
      setStep('otp');
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to send OTP');
    } finally {
      setLoading(false);
    }
  }

  async function onVerifyOtp(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const claims = await loginWithOtp({ ...idPayload(), otp } as VerifyOtpInput);
      router.replace(sanitizeRedirect(searchParams.get('redirect'), claims.tenantId));
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Verification failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="min-h-screen grid md:grid-cols-[5fr_4fr] bg-slate-50">
      <aside
        className="relative hidden md:flex flex-col justify-between p-10 text-white overflow-hidden"
        style={b.loginHeroUrl ? { backgroundImage: `url(${b.loginHeroUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' } : undefined}
      >
        <div className="absolute inset-0 bg-brand-gradient" />
        <div className="absolute inset-0 bg-brand-radial opacity-80" />
        <div className="relative z-10 flex items-center gap-3">
          <BrandLogo variant="dark" imgClassName="h-10" />
          <div>
            <div className="text-xl font-semibold tracking-tight">{b.shortName}</div>
            {b.affiliation && <div className="text-xs text-white/80">{b.affiliation}</div>}
          </div>
        </div>
        <div className="relative z-10 max-w-md">
          <h2 className="text-4xl font-semibold leading-tight tracking-tight">{b.schoolName}</h2>
          {b.tagline && <p className="mt-3 text-white/80 leading-relaxed">{b.tagline}</p>}
        </div>
        <div className="relative z-10 flex items-center justify-between text-xs text-white/70">
          <div>
            {b.contactPhone && <div>{b.contactPhone}</div>}
            {b.contactEmail && <div>{b.contactEmail}</div>}
            {b.websiteUrl && <div>{b.websiteUrl}</div>}
          </div>
          <div className="flex gap-3">
            {b.socialFacebook  && <a href={b.socialFacebook}  className="hover:text-white"><Facebook size={16} /></a>}
            {b.socialInstagram && <a href={b.socialInstagram} className="hover:text-white"><Instagram size={16} /></a>}
            {b.socialYoutube   && <a href={b.socialYoutube}   className="hover:text-white"><Youtube size={16} /></a>}
          </div>
        </div>
      </aside>

      <section className="flex items-center justify-center px-4 py-12 sm:py-8">
        <div className="w-full max-w-md">
          <div className="md:hidden mb-6 flex items-center justify-between">
            <BrandLogo withWordmark />
            {b.affiliation && <span className="text-[11px] text-slate-500">{b.affiliation}</span>}
          </div>

          <div className="bg-white rounded-brand shadow-sm border border-slate-200 p-8 animate-fade-in">
            <div className="mb-6">
              <div className="inline-flex items-center gap-2 text-xs font-medium text-primary bg-primary-soft px-2.5 py-1 rounded-full">
                <ShieldCheck size={12} /> Secure sign-in
              </div>
              <h1 className="text-2xl font-semibold tracking-tight mt-3">
                {step === 'otp' ? "Verify it's you" : 'Welcome back'}
              </h1>
              <p className="text-sm text-slate-500 mt-1.5">
                {step === 'otp'
                  ? `Enter the 6-digit code sent to ${identifier}.`
                  : method === 'PASSWORD'
                    ? 'Sign in with your password, or switch to OTP if you forgot it.'
                    : "We'll send a one-time code to confirm your identity."}
              </p>
            </div>

            {step === 'identifier' && (
              <div className="grid grid-cols-2 gap-2 p-1 bg-slate-100 rounded-brand mb-4">
                <MethodButton active={method === 'PASSWORD'} onClick={() => { setMethod('PASSWORD'); setError(null); }}>
                  <KeyRound size={14} /> Password
                </MethodButton>
                <MethodButton active={method === 'OTP'} onClick={() => { setMethod('OTP'); setError(null); }}>
                  <Smartphone size={14} /> OTP
                </MethodButton>
              </div>
            )}

            {error && (
              <div role="alert" className="mb-4 rounded-brand bg-danger/5 border border-danger/20 text-danger text-sm px-3 py-2.5">
                {error}
              </div>
            )}

            {step === 'identifier' ? (
              <form onSubmit={method === 'PASSWORD' ? onPasswordSubmit : onSendOtp} className="space-y-4">
                {SIGNUP_CHANNEL === 'BOTH' && (
                  <div className="grid grid-cols-2 gap-2 p-1 bg-slate-100 rounded-brand">
                    <ChannelButton active={channel === 'PHONE'} onClick={() => setChannel('PHONE')}>
                      <Phone size={14} /> Phone
                    </ChannelButton>
                    <ChannelButton active={channel === 'EMAIL'} onClick={() => setChannel('EMAIL')}>
                      <Mail size={14} /> Email
                    </ChannelButton>
                  </div>
                )}

                <label className="block">
                  <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">
                    {channel === 'PHONE' ? 'Mobile number' : 'Email address'}
                  </span>
                  <input
                    type={channel === 'PHONE' ? 'tel' : 'email'}
                    inputMode={channel === 'PHONE' ? 'numeric' : 'email'}
                    autoComplete={channel === 'PHONE' ? 'tel' : 'email'}
                    value={identifier}
                    onChange={(e) => setIdentifier(e.target.value)}
                    required
                    className="mt-2 block w-full rounded-brand border border-slate-300 px-4 py-3 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
                    placeholder={channel === 'PHONE' ? '+919876543210' : 'you@school.in'}
                    autoFocus
                  />
                </label>

                {method === 'PASSWORD' && (
                  <label className="block">
                    <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Password</span>
                    <input
                      type="password"
                      autoComplete="current-password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                      minLength={8}
                      className="mt-2 block w-full rounded-brand border border-slate-300 px-4 py-3 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
                      placeholder="••••••••"
                    />
                  </label>
                )}

                <Button type="submit" loading={loading}
                  disabled={!identifier || (method === 'PASSWORD' && password.length < 8)}
                  className="w-full" size="lg" glow>
                  {method === 'PASSWORD' ? 'Sign in' : 'Send code'}
                </Button>

                {method === 'PASSWORD' && (
                  <button
                    type="button"
                    onClick={() => { setMethod('OTP'); setError(null); }}
                    className="w-full text-sm text-slate-500 hover:text-slate-800 transition pt-1"
                  >
                    Forgot password? Sign in with OTP →
                  </button>
                )}

                <p className="text-xs text-slate-500 text-center pt-2">
                  No account?{' '}
                  <a href="/signup" className="text-primary font-medium hover:underline">
                    Sign up your school
                  </a>
                </p>
              </form>
            ) : (
              <form onSubmit={onVerifyOtp} className="space-y-4">
                <label className="block">
                  <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">6-digit code</span>
                  <input
                    type="text"
                    inputMode="numeric"
                    pattern="\d{6}"
                    maxLength={6}
                    value={otp}
                    onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                    required
                    autoFocus
                    className="mt-2 block w-full rounded-brand border border-slate-300 px-4 py-3 text-2xl tracking-[0.4em] text-center font-mono focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
                  />
                </label>
                <Button type="submit" loading={loading} disabled={otp.length !== 6} className="w-full" size="lg" glow>
                  Verify & sign in
                </Button>
                <button
                  type="button"
                  onClick={() => { setStep('identifier'); setOtp(''); setError(null); }}
                  className="w-full inline-flex items-center justify-center gap-1.5 text-sm text-slate-500 hover:text-slate-800 transition"
                >
                  <ArrowLeft size={14} /> Change {channel === 'PHONE' ? 'phone' : 'email'}
                </button>
              </form>
            )}
          </div>

          <p className="mt-6 text-center text-xs text-slate-400">
            Protected by school-grade encryption. © {new Date().getFullYear()} {b.schoolName}.
          </p>
        </div>
      </section>
    </main>
  );
}

function ChannelButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick}
      className={cn(
        'inline-flex items-center justify-center gap-1.5 py-1.5 rounded-[calc(var(--brand-radius)-2px)] text-sm font-medium transition',
        active ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900',
      )}>
      {children}
    </button>
  );
}

function MethodButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick}
      className={cn(
        'inline-flex items-center justify-center gap-1.5 py-2 rounded-[calc(var(--brand-radius)-2px)] text-sm font-medium transition',
        active ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900',
      )}>
      {children}
    </button>
  );
}

function sanitizeRedirect(raw: string | null, signedInTenantId: string): string {
  const fallback = `/tenants/${signedInTenantId}/dashboard`;
  if (!raw) return fallback;
  if (!raw.startsWith('/') || raw.startsWith('//')) return fallback;
  const m = raw.match(/^\/tenants\/([0-9a-f-]{36})(\/|$)/);
  if (m && m[1] !== signedInTenantId) return fallback;
  return raw;
}
