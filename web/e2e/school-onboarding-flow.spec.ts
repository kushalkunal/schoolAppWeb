/**
 * E2E: Full school onboarding flow
 *
 * Covers (in sequence):
 *  1. School registration — 3-step signup (details → OTP verify → set password)
 *  2. Admin login — password-based login
 *  3. Student registration — admin creates a new student
 *  4. Teacher registration — admin adds a CLASS_TEACHER via Settings → Staff
 *  5. Class assignment — admin assigns the teacher to a section
 *  6. Teacher takes attendance — teacher logs in, opens section, marks & submits
 *
 * All backend calls are intercepted via page.route() so the suite runs without a
 * live API server.
 */

import { test, expect, type Page } from '@playwright/test';

// ─── Shared test constants ────────────────────────────────────────────────────
const TENANT_ID  = 'e2e-school-001';
const CLASS_ID   = 'class-001';
const SECTION_ID = 'sec-001';
const STUDENT_ID = 'stu-001';
const ADMIN_ID   = 'staff-admin-001';
const TEACHER_ID = 'staff-teacher-001';
const TODAY      = new Date().toISOString().split('T')[0]!;

// ─── JWT helpers ─────────────────────────────────────────────────────────────
/** Builds a properly base64url-encoded JWT that `decodeJwt()` in the app can parse. */
function buildJwt(claims: Record<string, unknown>): string {
  const enc = (obj: object) =>
    Buffer.from(JSON.stringify(obj))
      .toString('base64')
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=/g, '');
  return `${enc({ alg: 'HS256', typ: 'JWT' })}.${enc(claims)}.e2e-sig`;
}

const ADMIN_JWT = buildJwt({
  sub: ADMIN_ID, tenantId: TENANT_ID, role: 'ADMIN', name: 'Admin User',
  iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 7200,
});
const TEACHER_JWT = buildJwt({
  sub: TEACHER_ID, tenantId: TENANT_ID, role: 'CLASS_TEACHER', name: 'Anita Sharma',
  iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 7200,
});

// ─── Mock data ────────────────────────────────────────────────────────────────
const MOCK_STUDENT = {
  id: STUDENT_ID, displayName: 'Ravi Kumar',
  firstName: 'Ravi', lastName: 'Kumar',
  admissionNumber: 'ADM001', gender: 'MALE',
  active: true, sectionId: SECTION_ID,
};

const MOCK_TEACHER = {
  id: TEACHER_ID, displayName: 'Anita Sharma',
  role: 'CLASS_TEACHER', phone: '9876543210',
  email: null, active: true,
};

const MOCK_CLASSES = [{
  id: CLASS_ID, name: 'Class 5',
  sections: [{ id: SECTION_ID, name: 'A', classTeacherId: null }],
}];

// ─── Response shape helpers ───────────────────────────────────────────────────
const ok = (data: unknown) => ({ success: true, data });

function authResp(jwt: string) {
  return ok({
    accessToken: jwt, refreshToken: 'rt-placeholder', expiresInSeconds: 7200,
    user: { id: ADMIN_ID, schoolId: TENANT_ID, displayName: 'Admin User', role: 'ADMIN' },
  });
}

// ─── URL predicate helpers ────────────────────────────────────────────────────
/** Match a request by its pathname (ignores query string and any base URL). */
const byPath = (path: string) => (url: URL) => url.pathname === path;
/** Match a request whose pathname starts with a prefix. */
const byPrefix = (prefix: string) => (url: URL) => url.pathname.startsWith(prefix);

const T = `/api/v1/tenants/${TENANT_ID}`; // base path for tenant-scoped routes

// ─── Re-usable mock setup ─────────────────────────────────────────────────────
/**
 * Registers all API mocks needed across the full flow.
 * Uses URL predicate functions so mocks work regardless of what base URL
 * the running dev server was built with.
 */
async function setupMocks(page: Page, jwt: string): Promise<void> {
  // ── Auth ──
  await page.route(byPath('/api/v1/auth/otp/send'),
    r => r.fulfill({ json: ok({}) }));
  await page.route(byPath('/api/v1/auth/otp/verify'),
    r => r.fulfill({ json: authResp(jwt) }));
  await page.route(byPath('/api/v1/auth/password/login'),
    r => r.fulfill({ json: authResp(jwt) }));
  await page.route(byPath('/api/v1/auth/password/set'),
    r => r.fulfill({ json: ok({}) }));
  await page.route(byPath('/api/v1/auth/token/refresh'),
    r => r.fulfill({ json: authResp(jwt) }));
  await page.route(byPath('/api/v1/auth/logout'),
    r => r.fulfill({ json: ok({}) }));

  // ── Tenant creation ──
  await page.route(byPath('/api/v1/tenants'), r => {
    if (r.request().method() === 'POST')
      return r.fulfill({ json: ok({ school: { id: TENANT_ID, name: 'E2E Test School' } }) });
    return r.continue();
  });

  // ── Tenant info & onboarding ──
  await page.route(byPath(`${T}`),
    r => r.fulfill({ json: ok({ id: TENANT_ID, name: 'E2E Test School', state: 'MH', city: 'Pune', board: 'CBSE', active: true }) }));
  await page.route(byPath(`${T}/onboarding-status`),
    r => r.fulfill({ json: ok({ complete: true }) }));

  // ── Per-school runtime branding (BrandingProvider in tenant layout) ──
  await page.route(
    byPath(`/api/v1/public/schools/${TENANT_ID}/branding`),
    r => r.fulfill({ json: ok(null) }),
  );

  // ── Dashboard ──
  await page.route(byPath(`${T}/dashboard`), r =>
    r.fulfill({
      json: ok({
        asOfDate: TODAY,
        attendance: { totalMarked: 0, present: 0, absent: 0, late: 0, halfDay: 0, leave: 0 },
        alerts: { total: 0, high: 0, critical: 0 },
        fees: { mtdCollectedPaise: 0, activeAtRiskCount: 0 },
        unmarkedSectionsCount: 0,
        topAtRisk: [],
      }),
    }));

  // ── Classes & bulk create ──
  await page.route(byPath(`${T}/classes`), r => {
    if (r.request().method() === 'GET')
      return r.fulfill({ json: ok(MOCK_CLASSES) });
    return r.continue();
  });
  await page.route(byPath(`${T}/classes/bulk`),
    r => r.fulfill({ json: ok(MOCK_CLASSES) }));

  // ── Students (list / create) — must be registered BEFORE the by-section route
  //    because Playwright checks routes in registration order (first match wins). ──
  await page.route(byPath(`${T}/students/by-section/${SECTION_ID}`),
    r => r.fulfill({ json: ok([MOCK_STUDENT]) }));
  await page.route(byPath(`${T}/students`), r => {
    if (r.request().method() === 'GET')
      return r.fulfill({
        json: { success: true, data: [MOCK_STUDENT], meta: { page: 0, size: 25, total: 1 } },
      });
    if (r.request().method() === 'POST')
      return r.fulfill({ json: ok(MOCK_STUDENT) });
    return r.continue();
  });

  // ── Staff (list / create) ──
  await page.route(byPath(`${T}/staff`), r => {
    if (r.request().method() === 'GET')
      return r.fulfill({ json: ok([MOCK_TEACHER]) });
    if (r.request().method() === 'POST')
      return r.fulfill({ json: ok(MOCK_TEACHER) });
    return r.continue();
  });

  // ── Class-teacher assignment ──
  await page.route(byPath(`${T}/sections/${SECTION_ID}/class-teacher`),
    r => r.fulfill({ json: ok({ id: SECTION_ID, name: 'A', classTeacherId: TEACHER_ID }) }));

  // ── Teacher's own sections ──
  await page.route(byPath(`${T}/sections/mine`),
    r => r.fulfill({ json: ok([{ id: SECTION_ID, name: 'A', classTeacherId: TEACHER_ID }]) }));

  // ── Attendance summary / unmarked ──
  await page.route(byPath(`${T}/attendance/summary`),
    r => r.fulfill({ json: ok({ totalMarked: 1, present: 1, absent: 0, late: 0, leave: 0 }) }));
  await page.route(byPath(`${T}/attendance/unmarked`),
    r => r.fulfill({ json: ok([]) }));

  // ── Section attendance (GET existing records / POST submit) ──
  await page.route(byPath(`${T}/sections/${SECTION_ID}/attendance`), r => {
    if (r.request().method() === 'GET')
      return r.fulfill({ json: ok([]) }); // no records yet → defaults everyone to PRESENT
    if (r.request().method() === 'POST')
      return r.fulfill({ json: ok({ sectionId: SECTION_ID, date: TODAY, totalStudents: 1, notificationsQueued: 1 }) });
    return r.continue();
  });

  // ── Risk & admissions (dashboard secondary widgets; swallow to avoid noise) ──
  await page.route(byPrefix(`${T}/risk`),   r => r.fulfill({ json: ok({ students: [] }) }));
  await page.route(byPrefix(`${T}/admissions`), r => r.fulfill({ json: ok([]) }));
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Seeds localStorage with a valid JWT so the app treats the page as logged-in.
 * Must be called before page.goto() since addInitScript runs at page load.
 */
async function seedAuth(page: Page, jwt: string): Promise<void> {
  await page.addInitScript(
    ({ accessToken, refreshToken, expiresAt }: { accessToken: string; refreshToken: string; expiresAt: number }) => {
      localStorage.setItem('sms.accessToken',  accessToken);
      localStorage.setItem('sms.refreshToken', refreshToken);
      localStorage.setItem('sms.expiresAt',    String(expiresAt));
    },
    { accessToken: jwt, refreshToken: 'rt-placeholder', expiresAt: Date.now() + 7_200_000 },
  );
}
test.describe('Full school onboarding flow', () => {

  // ── 1. School registration ──────────────────────────────────────────────────
  test('1 · School registration — 3-step signup', async ({ page }) => {
    await setupMocks(page, ADMIN_JWT);

    await page.goto('/signup');
    // Heading from StepHeader component for the 'account' step
    await expect(page.getByRole('heading', { name: 'Sign up your school' })).toBeVisible();

    // ── Step 1: account details ──
    await page.getByLabel('School name').fill('E2E Test School');
    await page.getByLabel('Principal name').fill('Admin User');

    // Select Email channel (env sets BOTH; click the Email picker)
    await page.getByRole('button', { name: /email/i }).click();
    await page.getByLabel('Email address').fill('admin@e2etest.in');

    await page.getByLabel('State', { exact: true }).fill('MH');
    await page.getByLabel('City', { exact: true }).fill('Pune');
    // Board defaults to CBSE — leave as-is

    await page.getByRole('button', { name: /continue.*verify email/i }).click();

    // ── Step 2: OTP verify ──
    await expect(page.getByRole('heading', { name: 'Verify your identity' })).toBeVisible();
    await expect(page.getByText('admin@e2etest.in')).toBeVisible();

    const otpInput = page.locator('input[inputmode="numeric"]');
    await otpInput.fill('123456');
    await page.getByRole('button', { name: /verify & continue/i }).click();

    // ── Step 3: set password ──
    await expect(page.getByRole('heading', { name: 'Set a password' })).toBeVisible();

    await page.locator('input[autocomplete="new-password"]').first().fill('SecurePass@1');
    await page.locator('input[autocomplete="new-password"]').last().fill('SecurePass@1');
    await page.getByRole('button', { name: /set password & go to dashboard/i }).click();

    // ── Redirected to tenant dashboard ──
    await expect(page).toHaveURL(new RegExp(`/tenants/${TENANT_ID}/dashboard`), { timeout: 10_000 });
  });

  // ── 2. Admin login ──────────────────────────────────────────────────────────
  test('2 · Admin login with password', async ({ page }) => {
    await setupMocks(page, ADMIN_JWT);

    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();

    // Password method is default; switch channel to Email
    await page.getByRole('button', { name: /email/i }).click();

    await page.locator('input[type="email"]').fill('admin@e2etest.in');
    await page.locator('input[type="password"]').fill('SecurePass@1');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();

    await expect(page).toHaveURL(new RegExp(`/tenants/${TENANT_ID}/dashboard`), { timeout: 10_000 });
  });

  // ── 3. Student registration ─────────────────────────────────────────────────
  test('3 · Student registration by admin', async ({ page }) => {
    await setupMocks(page, ADMIN_JWT);
    await seedAuth(page, ADMIN_JWT);

    await page.goto(`/tenants/${TENANT_ID}/students`);
    await expect(page.getByRole('heading', { name: 'Students' })).toBeVisible();

    // Open "New student" modal — Modal component has no role="dialog", detect by heading
    await page.getByRole('button', { name: /new student/i }).click();
    await expect(page.getByRole('heading', { name: 'New student' })).toBeVisible();

    // Fill student form
    await page.getByLabel('First name').fill('Ravi');
    await page.getByLabel('Last name').fill('Kumar');
    await page.getByLabel(/parent phone/i).fill('9876543210');

    // Class · Section native <select> — select by section ID value
    await page.locator('select[required]').selectOption(SECTION_ID);

    await page.getByRole('button', { name: 'Create student' }).click();

    // Modal closes; student row appears in the table
    await expect(page.getByRole('heading', { name: 'New student' })).not.toBeVisible();
    await expect(page.getByText('Ravi Kumar')).toBeVisible();
  });

  // ── 4. Teacher registration ─────────────────────────────────────────────────
  test('4 · Teacher registration by admin', async ({ page }) => {
    await setupMocks(page, ADMIN_JWT);
    await seedAuth(page, ADMIN_JWT);

    await page.goto(`/tenants/${TENANT_ID}/settings/staff`);
    await expect(page.getByRole('heading', { name: 'Staff' })).toBeVisible();

    // Open "Add staff member" modal
    await page.getByRole('button', { name: /add staff/i }).click();
    await expect(page.getByRole('heading', { name: 'Add staff member' })).toBeVisible();

    // Fill teacher form
    await page.getByLabel('First name').fill('Anita');
    await page.getByLabel('Last name').fill('Sharma');
    await page.getByLabel(/phone.*whatsapp/i).fill('9876543210');

    // Select CLASS_TEACHER role from the Role dropdown
    await page.getByLabel('Role').selectOption('CLASS_TEACHER');

    await page.getByRole('button', { name: /create \+ send invite/i }).click();

    // Modal closes; teacher appears in the staff table
    await expect(page.getByRole('heading', { name: 'Add staff member' })).not.toBeVisible();
    await expect(page.getByText('Anita Sharma')).toBeVisible();
    await expect(page.getByText('Class Teacher')).toBeVisible();
  });

  // ── 5. Assign class to teacher ──────────────────────────────────────────────
  test('5 · Admin assigns class section to teacher', async ({ page }) => {
    await setupMocks(page, ADMIN_JWT);
    await seedAuth(page, ADMIN_JWT);

    await page.goto(`/tenants/${TENANT_ID}/settings/classes`);
    await expect(page.getByRole('heading', { name: 'Classes & sections' })).toBeVisible();

    // Class 5 card visible with section A and "No class teacher" label
    await expect(page.getByRole('heading', { name: 'Class 5' })).toBeVisible();
    await expect(page.getByText('No class teacher')).toBeVisible();

    // Click the UserCheck icon button (title="Assign class teacher")
    await page.locator('button[title="Assign class teacher"]').click();
    await expect(page.getByRole('heading', { name: 'Assign class teacher' })).toBeVisible();

    // Select Anita Sharma from the dropdown and save
    await page.locator('select').last().selectOption(TEACHER_ID);
    await page.getByRole('button', { name: 'Assign', exact: true }).click();

    // Modal closes after successful assignment
    await expect(page.getByRole('heading', { name: 'Assign class teacher' })).not.toBeVisible();
  });

  // ── 6. Teacher takes attendance ─────────────────────────────────────────────
  test('6 · Teacher logs in and marks attendance', async ({ page }) => {
    // Override staff mock so /sections/mine returns teacher's section (teacher JWT)
    await setupMocks(page, TEACHER_JWT);
    await seedAuth(page, TEACHER_JWT);

    // ── Attendance home: teacher sees "My sections" ──
    await page.goto(`/tenants/${TENANT_ID}/attendance`);
    await expect(page.getByRole('heading', { name: 'Attendance' })).toBeVisible();
    await expect(page.getByText('Your assigned sections.')).toBeVisible();

    // Teacher's section displayed as "Section A"
    await expect(page.getByText('Section A')).toBeVisible();

    // Click "Mark attendance →" link for the section
    await page.getByRole('link', { name: /mark attendance/i }).click();

    // ── Section attendance page ──
    await expect(page).toHaveURL(new RegExp(`/attendance/${SECTION_ID}`));
    await expect(page.getByRole('heading', { name: 'Section attendance' })).toBeVisible();

    // Ravi Kumar appears, defaulted to PRESENT (green chip for first mark)
    await expect(page.getByText('Ravi Kumar')).toBeVisible();
    const presentBtn = page.locator('button').filter({ hasText: 'PRESENT' }).first();
    await expect(presentBtn).toBeVisible();
    await expect(presentBtn).toHaveClass(/bg-green/);

    // Tap once to cycle PRESENT → ABSENT
    await presentBtn.click();
    await expect(page.locator('button').filter({ hasText: 'ABSENT' }).first())
      .toHaveClass(/bg-red/);

    // Submit
    await page.getByRole('button', { name: /submit attendance/i }).click();

    // Success confirmation: "Saved · 1 parent alert(s) queued"
    await expect(page.getByText(/Saved.*parent alert/i)).toBeVisible({ timeout: 5_000 });
  });

});
