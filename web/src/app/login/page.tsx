'use client';

import { FormEvent, Suspense, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  ShieldCheck, Mail, Phone, ArrowLeft,
  KeyRound, Smartphone, Eye, EyeOff,
  GraduationCap, BookOpen, Pencil, Atom, FlaskConical,
  Calculator, Ruler, Music, Star, Trophy, Globe, Microscope, PenLine,
} from 'lucide-react';
import { useAuth, SendOtpInput, VerifyOtpInput, PasswordLoginInput } from '@/auth/AuthProvider';
import { isApiError } from '@/api/errors';
import { useBranding } from '@/brand/BrandingProvider';
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
      if (mustResetPassword) {
        router.replace(
          `/set-password?required=true&redirect=${encodeURIComponent(
            sanitizeRedirect(searchParams.get('redirect'), claims.tenantId),
          )}`,
        );
      } else {
        router.replace(sanitizeRedirect(searchParams.get('redirect'), claims.tenantId));
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
      router.replace(sanitizeRedirect(searchParams.get('redirect'), claims.tenantId));
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Verification failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main
      className="min-h-dvh relative flex flex-col items-center justify-center overflow-hidden px-4 py-10"
      style={{ background: 'linear-gradient(135deg, #8B82F6 0%, #A97FEA 40%, #5CC8F8 100%)' }}
    >
      {/* â”€â”€ Education-themed floating background decorations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */}
      <div className="pointer-events-none select-none" aria-hidden>
        {/* Soft grid */}
        <div
          className="absolute inset-0 opacity-[0.07]"
          style={{ backgroundImage: 'linear-gradient(white 1px,transparent 1px),linear-gradient(90deg,white 1px,transparent 1px)', backgroundSize: '48px 48px' }}
        />
        {/* Top row */}
        <GraduationCap size={180} className="absolute -top-8    -left-12    text-white opacity-[0.15] rotate-[-14deg]" />
        <BookOpen      size={120} className="absolute  top-4     right-2     text-white opacity-[0.13] rotate-[10deg]" />
        <Star          size={70}  className="absolute  top-14    left-[34%]  text-white opacity-[0.13] rotate-[20deg]" />
        <Ruler         size={90}  className="absolute  top-6     left-[56%]  text-white opacity-[0.12] rotate-[-30deg]" />
        {/* Middle row */}
        <Pencil        size={110} className="absolute  top-1/3   -left-6     text-white opacity-[0.13] rotate-[22deg]" />
        <Atom          size={130} className="absolute  top-1/3   -right-4    text-white opacity-[0.12] rotate-[-8deg]" />
        <Calculator    size={85}  className="absolute  top-[45%] left-[12%]  text-white opacity-[0.11]" />
        <Music         size={75}  className="absolute  top-[42%] right-[15%] text-white opacity-[0.12] rotate-[12deg]" />
        {/* Lower-middle row */}
        <FlaskConical  size={95}  className="absolute  bottom-1/3  -left-4   text-white opacity-[0.13] rotate-[8deg]" />
        <Globe         size={100} className="absolute  bottom-1/3   right-2  text-white opacity-[0.12] rotate-[-5deg]" />
        <Microscope    size={80}  className="absolute  bottom-[28%] left-[41%] text-white opacity-[0.11]" />
        <Trophy        size={70}  className="absolute  bottom-[36%] left-[23%] text-white opacity-[0.12] rotate-[-18deg]" />
        {/* Bottom row */}
        <PenLine       size={90}  className="absolute  bottom-16  -left-3    text-white opacity-[0.13] rotate-[15deg]" />
        <BookOpen      size={85}  className="absolute  bottom-8    right-5   text-white opacity-[0.12] rotate-[-20deg]" />
        <GraduationCap size={75}  className="absolute  bottom-6    left-1/2  text-white opacity-[0.11] rotate-[8deg]" />
        {/* Glowing orbs */}
        <div className="absolute -top-20   -left-20   w-80 h-80 rounded-full bg-white/20 blur-3xl" />
        <div className="absolute -bottom-16 -right-16  w-72 h-72 rounded-full bg-white/20 blur-3xl" />
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[500px] rounded-full bg-white/5 blur-3xl" />
      </div>
      {/* â”€â”€ Product wordmark â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */}
      <div className="relative z-10 flex items-center gap-2.5 mb-8">
        <div className="h-10 w-10 rounded-2xl bg-white/20 backdrop-blur-sm grid place-items-center shadow-lg">
          <GraduationCap size={20} className="text-white" />
        </div>
        <span className="text-white font-extrabold text-2xl tracking-tight">Vidya</span>
      </div>

      {/* â”€â”€ Login card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */}
      <div className="relative z-10 w-full max-w-sm bg-white/95 backdrop-blur-md rounded-3xl shadow-2xl overflow-hidden">
        {/* Coloured top accent bar */}
        <div className="h-1 w-full bg-brand-gradient" />

        <div className="px-7 pt-7 pb-8">

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

      {/* Footer */}
      <p className="relative z-10 mt-6 text-[11px] text-white/50 text-center">
        &copy; {new Date().getFullYear()} Vidya &middot; Built for every school
      </p>
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

function sanitizeRedirect(raw: string | null, signedInTenantId: string): string {
  const fallback = `/tenants/${signedInTenantId}/dashboard`;
  if (!raw) return fallback;
  if (!raw.startsWith('/') || raw.startsWith('//')) return fallback;
  const m = raw.match(/^\/tenants\/([0-9a-f-]{36})(\/|$)/);
  if (m && m[1] !== signedInTenantId) return fallback;
  return raw;
}

