'use client';

/**
 * Three-step signup — Slice 35.
 *
 *   1. **Account** — school details + identifier (phone OR email). POSTs /api/v1/tenants.
 *   2. **Verify** — OTP confirms the identifier. Issues a JWT.
 *   3. **Password** — optional but recommended: set a password so future logins can skip OTP.
 *      Skipping leaves the user with OTP-only access, which still works.
 *
 * After step 3 (or skip) we drop the user straight onto their tenant dashboard.
 */

import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Phone, Mail, ArrowLeft, KeyRound, CheckCircle2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import { isApiError } from '@/api/errors';
import { useAuth } from '@/auth/AuthProvider';
import { cn } from '@/lib/utils';

type Board = 'CBSE' | 'ICSE' | 'STATE' | 'IGCSE' | 'OTHER';
type Channel = 'PHONE' | 'EMAIL' | 'BOTH';
const CHANNEL = (process.env.NEXT_PUBLIC_SIGNUP_CHANNEL ?? 'BOTH') as Channel;

type Step = 'account' | 'verify' | 'password' | 'done';

export default function SignupPage() {
  const router = useRouter();
  const { sendOtp, loginWithOtp, setPassword } = useAuth();

  const [step, setStep] = useState<Step>('account');
  const [pickedChannel, setPickedChannel] = useState<'PHONE' | 'EMAIL'>(
    CHANNEL === 'EMAIL' ? 'EMAIL' : 'PHONE'
  );
  const [form, setForm] = useState({
    schoolName: '',
    principalName: '',
    phone: '',
    email: '',
    state: '',
    city: '',
    board: 'CBSE' as Board,
  });
  const [otp, setOtp] = useState('');
  const [password, setPasswordValue] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [tenantId, setTenantId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) =>
    setForm((f) => ({ ...f, [k]: v }));

  const identifier = pickedChannel === 'PHONE' ? form.phone.trim() : form.email.trim();
  const idPayload = () =>
    pickedChannel === 'PHONE' ? { phone: form.phone.trim() } : { email: form.email.trim() };

  // ---------------- Step 1: create tenant + send OTP ----------------
  async function onAccountSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const phone = form.phone.trim();
    const email = form.email.trim();
    if (pickedChannel === 'PHONE' && !phone) return setError('Phone number is required.');
    if (pickedChannel === 'EMAIL' && !email) return setError('Email address is required.');

    setLoading(true);
    try {
      const body = {
        schoolName: form.schoolName.trim(),
        principalName: form.principalName.trim(),
        phone: phone || undefined,
        email: email || undefined,
        state: form.state.trim(),
        city: form.city.trim() || undefined,
        board: form.board,
      };
      const res = await apiClient.post('/api/v1/tenants', body);
      setTenantId(res.data.data.school.id);
      // Auto-trigger OTP send so step 2 just collects the code.
      await sendOtp(idPayload());
      setStep('verify');
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Signup failed');
    } finally {
      setLoading(false);
    }
  }

  // ---------------- Step 2: verify identifier via OTP ----------------
  async function onVerifyOtp(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await loginWithOtp({ ...idPayload(), otp });
      // Logged in. Now offer password setup.
      setStep('password');
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Invalid code');
    } finally {
      setLoading(false);
    }
  }

  async function onResendOtp() {
    setError(null);
    try {
      await sendOtp(idPayload());
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Could not resend code');
    }
  }

  // ---------------- Step 3 (optional): set password ----------------
  async function onSetPassword(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (password.length < 8) return setError('Password must be at least 8 characters.');
    if (password !== passwordConfirm) return setError('Passwords do not match.');
    setLoading(true);
    try {
      await setPassword(password);
      finish();
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Could not set password');
    } finally {
      setLoading(false);
    }
  }

  function skipPassword() {
    finish();
  }

  function finish() {
    setStep('done');
    if (tenantId) router.replace(`/tenants/${tenantId}/dashboard`);
    else router.replace('/login');
  }

  // ---------------- Render ----------------
  return (
    <main className="min-h-screen flex items-center justify-center px-4 py-8 bg-slate-50">
      <div className="w-full max-w-md bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <StepHeader step={step} />

        {error && (
          <div role="alert" className="text-sm text-danger mb-4 bg-red-50 border border-red-200 rounded px-3 py-2">
            {error}
          </div>
        )}

        {step === 'account' && (
          <form onSubmit={onAccountSubmit} className="space-y-3">
            <Field label="School name" value={form.schoolName} onChange={(v) => set('schoolName', v)} required />
            <Field label="Principal name" value={form.principalName} onChange={(v) => set('principalName', v)} required />

            {CHANNEL === 'BOTH' && (
              <div className="grid grid-cols-2 gap-2 p-1 bg-slate-100 rounded">
                <PickerButton active={pickedChannel === 'PHONE'} onClick={() => setPickedChannel('PHONE')}>
                  <Phone size={14} /> Phone
                </PickerButton>
                <PickerButton active={pickedChannel === 'EMAIL'} onClick={() => setPickedChannel('EMAIL')}>
                  <Mail size={14} /> Email
                </PickerButton>
              </div>
            )}
            {(CHANNEL === 'PHONE' || (CHANNEL === 'BOTH' && pickedChannel === 'PHONE')) && (
              <Field label="Phone (with country code)" type="tel" value={form.phone}
                onChange={(v) => set('phone', v)} required placeholder="+919876543210" />
            )}
            {(CHANNEL === 'EMAIL' || (CHANNEL === 'BOTH' && pickedChannel === 'EMAIL')) && (
              <Field label="Email address" type="email" value={form.email}
                onChange={(v) => set('email', v)} required placeholder="you@school.in" />
            )}

            <Field label="State" value={form.state} onChange={(v) => set('state', v)} required />
            <Field label="City" value={form.city} onChange={(v) => set('city', v)} />

            <label className="block">
              <span className="text-sm text-slate-700">Board</span>
              <select value={form.board} onChange={(e) => set('board', e.target.value as Board)}
                className="mt-1 block w-full rounded border border-slate-300 px-3 py-2 text-sm">
                <option value="CBSE">CBSE</option>
                <option value="ICSE">ICSE</option>
                <option value="STATE">State Board</option>
                <option value="IGCSE">IGCSE</option>
                <option value="OTHER">Other</option>
              </select>
            </label>

            <button type="submit" disabled={loading}
              className="w-full bg-primary text-white py-2.5 rounded text-sm font-medium disabled:opacity-50 mt-2">
              {loading ? 'Creating & sending OTP…' : 'Continue → verify ' + (pickedChannel === 'PHONE' ? 'phone' : 'email')}
            </button>
            <p className="text-xs text-slate-500 text-center pt-1">
              Already have an account? <a href="/login" className="text-primary font-medium hover:underline">Sign in</a>
            </p>
          </form>
        )}

        {step === 'verify' && (
          <form onSubmit={onVerifyOtp} className="space-y-4">
            <p className="text-sm text-slate-600">
              We sent a 6-digit code to <b>{identifier}</b>.
            </p>
            <label className="block">
              <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">6-digit code</span>
              <input type="text" inputMode="numeric" pattern="\d{6}" maxLength={6}
                value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                required autoFocus
                className="mt-2 block w-full rounded border border-slate-300 px-4 py-3 text-2xl tracking-[0.4em] text-center font-mono focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition" />
            </label>
            <button type="submit" disabled={loading || otp.length !== 6}
              className="w-full bg-primary text-white py-2.5 rounded text-sm font-medium disabled:opacity-50">
              {loading ? 'Verifying…' : 'Verify & continue'}
            </button>
            <div className="flex items-center justify-between text-xs">
              <button type="button" onClick={() => { setStep('account'); setOtp(''); setError(null); }}
                className="inline-flex items-center gap-1.5 text-slate-500 hover:text-slate-800 transition">
                <ArrowLeft size={12} /> Change details
              </button>
              <button type="button" onClick={onResendOtp}
                className="text-primary hover:underline">Resend code</button>
            </div>
          </form>
        )}

        {step === 'password' && (
          <form onSubmit={onSetPassword} className="space-y-4">
            <div className="rounded bg-emerald-50 border border-emerald-200 text-emerald-700 text-sm px-3 py-2 inline-flex items-center gap-2">
              <CheckCircle2 size={14} /> {identifier} verified
            </div>
            <p className="text-sm text-slate-600">
              Set a password so you can sign in without an OTP next time. You can always skip this and use OTP only.
            </p>
            <label className="block">
              <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">New password</span>
              <input type="password" autoComplete="new-password" minLength={8}
                value={password} onChange={(e) => setPasswordValue(e.target.value)}
                className="mt-2 block w-full rounded border border-slate-300 px-3 py-2.5 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition"
                placeholder="At least 8 characters" />
            </label>
            <label className="block">
              <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Confirm password</span>
              <input type="password" autoComplete="new-password" minLength={8}
                value={passwordConfirm} onChange={(e) => setPasswordConfirm(e.target.value)}
                className="mt-2 block w-full rounded border border-slate-300 px-3 py-2.5 text-sm focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/15 transition" />
            </label>
            <button type="submit" disabled={loading || password.length < 8 || password !== passwordConfirm}
              className="w-full bg-primary text-white py-2.5 rounded text-sm font-medium disabled:opacity-50 inline-flex items-center justify-center gap-2">
              <KeyRound size={14} /> {loading ? 'Saving…' : 'Set password & go to dashboard'}
            </button>
            <button type="button" onClick={skipPassword}
              className="w-full text-sm text-slate-500 hover:text-slate-800 transition">
              Skip for now — I'll use OTP each time
            </button>
          </form>
        )}
      </div>
    </main>
  );
}

function StepHeader({ step }: { step: Step }) {
  const map: Record<Step, { num: number; title: string; sub: string }> = {
    account:  { num: 1, title: 'Sign up your school', sub: "You'll start on the free plan with a 14-day trial of every feature." },
    verify:   { num: 2, title: 'Verify your identity', sub: 'Enter the OTP we just sent.' },
    password: { num: 3, title: 'Set a password', sub: 'Optional — skip if you prefer OTP-only login.' },
    done:     { num: 3, title: 'All set!', sub: 'Redirecting…' },
  };
  const m = map[step];
  return (
    <div className="mb-6">
      <div className="flex items-center gap-2 text-xs text-slate-500 mb-2">
        {[1, 2, 3].map((n) => (
          <div key={n} className={cn('h-1.5 flex-1 rounded-full', n <= m.num ? 'bg-primary' : 'bg-slate-200')} />
        ))}
      </div>
      <h1 className="text-2xl font-semibold">{m.title}</h1>
      <p className="text-sm text-slate-500 mt-1">{m.sub}</p>
    </div>
  );
}

function PickerButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick}
      className={cn(
        'inline-flex items-center justify-center gap-1.5 py-1.5 rounded text-sm font-medium transition',
        active ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900',
      )}>
      {children}
    </button>
  );
}

function Field({ label, value, onChange, type = 'text', required = false, placeholder }: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  required?: boolean;
  placeholder?: string;
}) {
  return (
    <label className="block">
      <span className="text-sm text-slate-700">{label}</span>
      <input type={type} value={value} onChange={(e) => onChange(e.target.value)}
        required={required} placeholder={placeholder}
        className="mt-1 block w-full rounded border border-slate-300 px-3 py-2 text-sm" />
    </label>
  );
}
