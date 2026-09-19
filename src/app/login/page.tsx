'use client';

import { FormEvent, Suspense, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  ShieldCheck, Mail, Phone, ArrowLeft,
  KeyRound, Smartphone, Eye, EyeOff, CheckCircle2,
} from 'lucide-react';
import { useAuth, SendOtpInput, VerifyOtpInput, PasswordLoginInput } from '@/auth/AuthProvider';
import { isApiError } from '@/api/errors';
import { useBranding } from '@/brand/BrandingProvider';
import { PLATFORM } from '@/brand/branding.config';
import { resolveTenantSlug } from '@/lib/tenantSlug';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

type Channel = 'PHONE' | 'EMAIL' | 'BOTH';
const SIGNUP_CHANNEL = (process.env.NEXT_PUBLIC_SIGNUP_CHANNEL ?? 'BOTH') as Channel;

export default function LoginPage() {
  return (
    <Suspense fallback={<main className="min-h-dvh grid place-items-center" />}>
      <LoginInner />
    </Suspense>
  );
}

function LoginInner() {
  const b = useBranding();
  const { sendOtp, loginWithOtp, loginWithPassword } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  const [method, setMethod]     = useState<'PASSWORD' | 'OTP'>('PASSWORD');
  const [step, setStep]         = useState<'identifier' | 'otp'>('identifier');
  const [channel, setChannel]   = useState<'PHONE' | 'EMAIL'>(
    SIGNUP_CHANNEL === 'EMAIL' ? 'EMAIL' : 'PHONE',
  );
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword]     = useState('');
  const [showPw, setShowPw]         = useState(false);
  const [otp, setOtp]               = useState('');
  const [loading, setLoading]       = useState(false);
  const [error, setError]           = useState<string | null>(null);

  const idPayload = (): SendOtpInput =>
    channel === 'PHONE' ? { phone: identifier } : { email: identifier };

  async function onPasswordSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const input: PasswordLoginInput = { ...idPayload(), password };
      const { claims, mustResetPassword } = await loginWithPassword(input);
      const slug = await resolveTenantSlug(claims.tenantId);
      if (mustResetPassword) {
        router.replace(
          `/set-password?required=true&redirect=${encodeURIComponent(
            sanitizeRedirect(searchParams.get('redirect'), slug),
          )}`,
        );
      } else {
        router.replace(sanitizeRedirect(searchParams.get('redirect'), slug));
      }
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
      const slug = await resolveTenantSlug(claims.tenantId);
      router.replace(sanitizeRedirect(searchParams.get('redirect'), slug));
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Verification failed');
    } finally {
      setLoading(false);
    }
  }

  const year = new Date().getFullYear();
  return (
    <main className="min-h-dvh flex bg-white">
      {/* â”€â”€ LEFT brand panel (desktop) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */}
      <aside
        className="relative hidden lg:flex lg:w-[52%] flex-col justify-between overflow-hidden px-12 py-10 text-white"
        style={{ background: 'radial-gradient(circle at top left, rgba(255,255,255,0.12), transparent 28%), linear-gradient(135deg, #bf112f 0%, #9d0017 38%, #7a000d 100%)' }}
      >
        <div aria-hidden className="pointer-events-none absolute inset-0 opacity-[0.12]"
          style={{ backgroundImage: 'linear-gradient(white 1px,transparent 1px),linear-gradient(90deg,white 1px,transparent 1px)', backgroundSize: '42px 42px' }} />
        <div aria-hidden className="pointer-events-none absolute -top-24 -left-24 h-96 w-96 rounded-full bg-white/10 blur-3xl" />
        <div aria-hidden className="pointer-events-none absolute -bottom-24 -right-16 h-80 w-80 rounded-full bg-white/10 blur-3xl" />

        <div className="relative z-10 flex items-center gap-4">
          <div className="h-14 w-14 rounded-[18px] bg-white/95 grid place-items-center shadow-[0_18px_40px_rgba(0,0,0,0.18)] p-2 ring-1 ring-white/60">
            <img src="/brand/logo.svg" alt={`${PLATFORM.name} logo`} className="h-full w-full object-contain" />
          </div>
          <div className="flex flex-col">
            <div className="flex items-baseline gap-1 text-[2.1rem] font-black leading-none tracking-[-0.08em]">
              <span className="text-white">Scalio</span>
              <span className="text-red-100/80">Campus</span>
            </div>
            <a
              href={PLATFORM.poweredByUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-1 text-[11px] font-medium text-red-100/90 underline decoration-red-100/60 underline-offset-2 hover:text-white"
            >
              Powered by ScalioLab
            </a>
          </div>
        </div>

        <div className="relative z-10 max-w-md">
          <h2 className="text-4xl font-extrabold leading-[1.1] tracking-tight">
            The operating system for your school.
          </h2>
          <p className="mt-4 text-white/70 text-base leading-relaxed">
            Admissions, attendance, exams, fees and communication — one secure, premium platform for every role.
          </p>
          <ul className="mt-8 space-y-3 text-sm text-white/85">
            {[
              'Role dashboards for principals, teachers & accountants',
              'Exams, admit cards & results — end to end',
              'Fee collection, receipts & defaulter tracking',
            ].map((t) => (
              <li key={t} className="flex items-center gap-2.5">
                <CheckCircle2 size={16} className="shrink-0 text-white/90" /> {t}
              </li>
            ))}
          </ul>
        </div>

        <p className="relative z-10 text-xs text-red-100/80">
          <a href={PLATFORM.poweredByUrl} target="_blank" rel="noopener noreferrer" className="underline decoration-red-100/60 underline-offset-2 hover:text-white">
            {PLATFORM.poweredBy}
          </a>
          {' '}&middot; &copy; {year} {PLATFORM.company}
        </p>
      </aside>

      {/* â”€â”€ RIGHT form column â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */}
      <div className="flex-1 flex flex-col min-h-dvh">
        <div className="flex-1 flex items-center justify-center px-5 py-10">
          <div className="w-full max-w-[420px] rounded-[28px] border border-slate-200 bg-white/85 p-7 shadow-[0_30px_80px_rgba(15,23,42,0.12)] backdrop-blur-sm lg:bg-white lg:p-8">
            {/* Compact brand header — shown when the left panel is hidden */}
            <div className="mb-8 flex items-center gap-3 lg:hidden">
              <div className="h-11 w-11 rounded-xl bg-primary grid place-items-center shadow-lg shadow-red-200 p-1.5">
                <img src="/brand/logo.svg" alt={`${PLATFORM.name} logo`} className="h-full w-full object-contain" />
              </div>
              <div className="flex flex-col leading-none">
                <div className="flex items-baseline gap-1 text-xl font-black tracking-[-0.07em]">
                  <span className="text-primary">Scalio</span>
                  <span className="text-slate-900">Campus</span>
                </div>
                <a
                  href={PLATFORM.poweredByUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-1 text-[10px] font-medium text-slate-500 underline decoration-slate-300 underline-offset-2 hover:text-slate-700"
                >
                  Powered by ScalioLab
                </a>
              </div>
            </div>

          {/* Heading */}
          <div className="mb-6">
            <div className="inline-flex items-center gap-1.5 text-xs font-semibold text-primary bg-primary-soft px-3 py-1.5 rounded-full mb-3">
              <ShieldCheck size={12} /> Secure sign-in
            </div>
            <h1 className="text-2xl font-extrabold tracking-tight text-slate-900 leading-tight">
              {step === 'otp' ? "Verify it's you" : 'Welcome back'}
            </h1>
            <p className="text-sm text-slate-500 mt-1">
              {step === 'otp'
                ? `Enter the 6-digit code sent to ${identifier}.`
                : method === 'PASSWORD'
                  ? 'Sign in with your password, or use OTP.'
                  : "We'll send a one-time code to your phone or email."}
            </p>
          </div>

          {/* Method toggle */}
          {step === 'identifier' && (
            <div className="flex gap-1 p-1 bg-slate-100 rounded-xl mb-5">
              <SegTab active={method === 'PASSWORD'} onClick={() => { setMethod('PASSWORD'); setError(null); }}>
                <KeyRound size={13} /> Password
              </SegTab>
              <SegTab active={method === 'OTP'} onClick={() => { setMethod('OTP'); setError(null); }}>
                <Smartphone size={13} /> OTP
              </SegTab>
            </div>
          )}

          {/* Error */}
          {error && (
            <div role="alert" className="mb-4 rounded-xl bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3">
              {error}
            </div>
          )}

          {/* â”€â”€ Identifier step â”€â”€ */}
          {step === 'identifier' ? (
            <form onSubmit={method === 'PASSWORD' ? onPasswordSubmit : onSendOtp} className="space-y-3.5">

              {SIGNUP_CHANNEL === 'BOTH' && (
                <div className="flex gap-1 p-1 bg-slate-100 rounded-xl">
                  <SegTab active={channel === 'PHONE'} onClick={() => setChannel('PHONE')}>
                    <Phone size={13} /> Phone
                  </SegTab>
                  <SegTab active={channel === 'EMAIL'} onClick={() => setChannel('EMAIL')}>
                    <Mail size={13} /> Email
                  </SegTab>
                </div>
              )}

              <div className="relative">
                <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none">
                  {channel === 'PHONE' ? <Phone size={15} /> : <Mail size={15} />}
                </span>
                <input
                  type={channel === 'PHONE' ? 'tel' : 'email'}
                  inputMode={channel === 'PHONE' ? 'numeric' : 'email'}
                  autoComplete={channel === 'PHONE' ? 'tel' : 'email'}
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                  required
                  autoFocus
                  className="block w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-4 py-3.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-primary focus:bg-white focus:ring-2 focus:ring-primary/15 transition min-h-[48px]"
                  placeholder={channel === 'PHONE' ? '+91 98765 43210' : 'you@school.in'}
                />
              </div>

              {method === 'PASSWORD' && (
                <div className="relative">
                  <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none">
                    <KeyRound size={15} />
                  </span>
                  <input
                    type={showPw ? 'text' : 'password'}
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                    minLength={8}
                    className="block w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-12 py-3.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:border-primary focus:bg-white focus:ring-2 focus:ring-primary/15 transition min-h-[48px]"
                    placeholder="Password"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPw((v) => !v)}
                    className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition"
                    aria-label={showPw ? 'Hide password' : 'Show password'}
                  >
                    {showPw ? <EyeOff size={15} /> : <Eye size={15} />}
                  </button>
                </div>
              )}

              <Button
                type="submit"
                loading={loading}
                disabled={!identifier || (method === 'PASSWORD' && password.length < 8)}
                className="w-full min-h-[50px] text-base font-semibold rounded-xl mt-1"
                size="lg"
                glow
              >
                {method === 'PASSWORD' ? 'Sign in' : 'Send code'}
              </Button>

              {method === 'PASSWORD' && (
                <button
                  type="button"
                  onClick={() => { setMethod('OTP'); setError(null); }}
                  className="w-full text-sm text-slate-500 hover:text-primary transition py-1 min-h-[44px]"
                >
                  Forgot password? Sign in with OTP &rarr;
                </button>
              )}

              <div className="relative flex items-center gap-3 py-1">
                <div className="flex-1 h-px bg-slate-200" />
                <span className="text-xs text-slate-400">or</span>
                <div className="flex-1 h-px bg-slate-200" />
              </div>

              <p className="text-sm text-slate-500 text-center">
                No account?{' '}
                <a href="/signup" className="text-primary font-semibold hover:underline">
                  Sign up your school
                </a>
              </p>
            </form>

          ) : (
            <form onSubmit={onVerifyOtp} className="space-y-3.5">
              <p className="text-xs text-slate-500 bg-slate-50 rounded-xl px-4 py-3 border border-slate-200">
                Sent to <span className="font-semibold text-slate-700">{identifier}</span>
              </p>
              <input
                type="text"
                inputMode="numeric"
                pattern="\d{6}"
                maxLength={6}
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                required
                autoFocus
                className="block w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-4 text-3xl tracking-[0.5em] text-center font-mono text-slate-900 focus:outline-none focus:border-primary focus:bg-white focus:ring-2 focus:ring-primary/15 transition min-h-[68px]"
                placeholder="------"
              />
              <Button
                type="submit"
                loading={loading}
                disabled={otp.length !== 6}
                className="w-full min-h-[50px] text-base font-semibold rounded-xl"
                size="lg"
                glow
              >
                Verify &amp; sign in
              </Button>
              <button
                type="button"
                onClick={() => { setStep('identifier'); setOtp(''); setError(null); }}
                className="w-full inline-flex items-center justify-center gap-1.5 text-sm text-slate-500 hover:text-slate-800 transition min-h-[44px]"
              >
                <ArrowLeft size={14} /> Change {channel === 'PHONE' ? 'phone' : 'email'}
              </button>
            </form>
          )}
          </div>
        </div>
        {/* Mobile footer attribution — the left brand panel carries this on desktop */}
        <p className="lg:hidden pb-6 text-center text-[11px] text-slate-400">
          <a href={PLATFORM.poweredByUrl} target="_blank" rel="noopener noreferrer" className="underline decoration-slate-300 underline-offset-2 hover:text-slate-600">
            {PLATFORM.poweredBy}
          </a>
          {' '}&middot; &copy; {year} {PLATFORM.company}
        </p>
      </div>
    </main>
  );
}

function SegTab({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'flex-1 inline-flex items-center justify-center gap-1.5 py-2 rounded-[10px] text-sm font-medium transition-all min-h-[40px]',
        active
          ? 'bg-white text-slate-900 shadow-sm'
          : 'text-slate-500 hover:text-slate-800',
      )}
    >
      {children}
    </button>
  );
}

function sanitizeRedirect(raw: string | null, slug: string): string {
  const fallback = `/tenants/${slug}/dashboard`;
  if (!raw || !raw.startsWith('/') || raw.startsWith('//')) return fallback;
  // Only honour same-app tenant deep-links, and canonicalise their tenant segment to the slug
  // (so a bookmarked URL carrying the old UUID lands on the clean slug instead).
  const parts = raw.split('/');
  if (parts[1] === 'tenants' && parts.length >= 3 && parts[2]) {
    parts[2] = slug;
    return parts.join('/');
  }
  return fallback;
}

