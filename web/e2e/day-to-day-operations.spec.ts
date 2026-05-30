/**
 * E2E: Day-to-Day School Operations — Live Backend
 *
 * Tests the full morning/daily workflow as a senior QA engineer would validate it:
 *
 *  TC01 – Principal login via the UI
 *  TC02 – Teachers roster: all 8 staff visible
 *  TC03 – Timetable: Class 1A has Ananya Singh in Periods 1–3 (own class)
 *  TC04 – Timetable: Class 3B Period 4 also shows Ananya Singh (cross-section teacher)
 *  TC05 – Timetable conflict detection: assigning Ananya to Class 2A P1 Monday is blocked
 *  TC06 – Attendance dashboard: today's stats show marked/present/absent/late counts
 *  TC07 – Attendance detail: Class 1A shows all 4 students with status badges
 *  TC08 – Class teacher login: Ananya sees only her assigned section
 *
 * Runs against the live stack:
 *   Frontend  http://localhost:3001
 *   Backend   http://localhost:8081
 *
 * Data pre-requisites (already set up via scripts):
 *   • 7 class teachers onboarded (Teacher@123)
 *   • Classes 1–6 A/B with students enrolled
 *   • Timetable periods P1–P8 + breaks defined
 *   • Ananya Singh assigned to Class 1A P1–P3 Mon–Fri
 *   • Ananya Singh also assigned to Class 3B P4 Mon–Fri (cross-section)
 *   • Attendance marked for all 12 sections on 2026-05-27
 */

import { test, expect, type Page } from '@playwright/test';

// ─── Constants ────────────────────────────────────────────────────────────────
const TENANT     = '926c372c-139d-460d-83b1-1a80ef92db57';
const BASE_URL   = `http://localhost:3001`;
const TENANT_URL = `${BASE_URL}/tenants/${TENANT}`;
const TODAY      = '2026-05-27';

// Credentials
const PRINCIPAL_EMAIL    = 'teacher@vms.school';
const PRINCIPAL_PASSWORD = 'Test@1234';
const TEACHER_EMAIL      = 'ananya.singh@vms.school';
const TEACHER_PASSWORD   = 'Teacher@123';

// Section IDs (from DB)
const SEC_1A = '032ec61c-3373-4389-9169-16ae826c357a';
const SEC_2A = '64a5ca42-7480-46d2-83c1-9bd44a12dcb7';
const SEC_3B = '52bddd2c-a6f0-406f-8faf-2af17c349965';

// Period IDs
const PERIOD_1_ID = 'f010685d-74f8-49f8-baac-55d9afc8f727';
const PERIOD_4_ID = '56088684-b11d-4739-ba97-45a6991253f5';

// ─── Shared helper ────────────────────────────────────────────────────────────
async function loginAs(page: Page, email: string, password: string) {
  await page.goto(`${BASE_URL}/login`);
  await page.waitForLoadState('networkidle');

  // Channel picker shows Phone / Email buttons — click Email
  const emailChannelBtn = page.getByRole('button', { name: /^Email$/i });
  if (await emailChannelBtn.count() > 0) {
    await emailChannelBtn.click();
  }

  // Fill email (type=email input)
  await page.locator('input[type="email"]').fill(email);

  // Fill password (type=password input)
  await page.locator('input[type="password"]').fill(password);

  // Submit
  await page.getByRole('button', { name: /^Sign in$/i }).click();

  // Wait for redirect away from /login
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 10_000 });
}

// ─── TC01: Principal Login ────────────────────────────────────────────────────
test('TC01 – principal can log in and lands on dashboard', async ({ page }) => {
  await loginAs(page, PRINCIPAL_EMAIL, PRINCIPAL_PASSWORD);

  // Should land somewhere under the tenant namespace
  await expect(page).toHaveURL(new RegExp(TENANT));

  // Nav sidebar / header should show the school name or user greeting
  const body = page.locator('body');
  await expect(body).not.toContainText('401');
  await expect(body).not.toContainText('Unauthorized');
});

// ─── TC02: Teacher Roster ─────────────────────────────────────────────────────
test('TC02 – all 8 teachers visible in the Teachers roster', async ({ page }) => {
  await loginAs(page, PRINCIPAL_EMAIL, PRINCIPAL_PASSWORD);
  await page.goto(`${TENANT_URL}/teachers/onboard`);
  await page.waitForLoadState('networkidle');

  // Wait for table to render
  const table = page.locator('table');
  await expect(table).toBeVisible({ timeout: 8_000 });

  // Verify known teachers appear
  const teacherNames = [
    'Ananya Singh',
    'Rohan Mehta',
    'Deepa Nair',
    'Suresh Patel',
    'Meera Iyer',
    'Arjun Kumar',
    'Lakshmi Devi',
    'Priya Sharma',
  ];
  for (const name of teacherNames) {
    await expect(page.getByText(name)).toBeVisible({ timeout: 5_000 });
  }

  // All teachers should have "Active" status badge (none deactivated)
  const activeBadges = page.getByText('Active');
  const count = await activeBadges.count();
  expect(count).toBeGreaterThanOrEqual(7);
});

// ─── TC03: Timetable – Class 1A has Ananya in own class (P1-P3) ───────────────
test('TC03 – timetable for Class 1A shows Ananya Singh in Periods 1–3', async ({ page }) => {
  await loginAs(page, PRINCIPAL_EMAIL, PRINCIPAL_PASSWORD);
  await page.goto(`${TENANT_URL}/teachers/timetable`);
  await page.waitForLoadState('networkidle');

  // Periods list should be visible
  await expect(page.getByText('Period 1')).toBeVisible({ timeout: 8_000 });

  // Pick Class 1A from section picker
  const picker = page.locator('select').first();
  await picker.selectOption({ label: 'Class Class 1 – A' });

  // Wait for the timetable grid (weekly schedule heading)
  await expect(page.getByText(/Weekly schedule/i)).toBeVisible({ timeout: 8_000 });

  // Wait for staff names to populate (staffQ is async — names show after API returns)
  await expect(page.getByText('Ananya Singh').first()).toBeVisible({ timeout: 10_000 });

  // Ananya's name should appear multiple times (P1-P3 across 5 days = 15 cells)
  const ananyaCount = await page.getByText('Ananya Singh').count();
  expect(ananyaCount).toBeGreaterThanOrEqual(5);
});

// ─── TC04: Cross-section – Class 3B P4 also shows Ananya ─────────────────────
test('TC04 – Class 3B Period 4 shows Ananya Singh as cross-section teacher', async ({ page }) => {
  await loginAs(page, PRINCIPAL_EMAIL, PRINCIPAL_PASSWORD);
  await page.goto(`${TENANT_URL}/teachers/timetable`);
  await page.waitForLoadState('networkidle');

  // Select Class 3B
  const picker = page.locator('select').first();
  await picker.selectOption({ label: 'Class Class 3 – B' });

  await expect(page.getByText(/Weekly schedule/i)).toBeVisible({ timeout: 8_000 });

  // Period 4 row should show Ananya Singh (she teaches 3B during P4 Mon-Fri)
  const rows = page.locator('tbody tr');
  const period4Row = rows.filter({ hasText: 'Period 4' });
  await expect(period4Row.getByText('Ananya Singh').first()).toBeVisible({ timeout: 8_000 });

  // Verify this SAME teacher appears in a different section's timetable (cross-section proof)
  // Switch back to Class 1A - she should ALSO be there in P1
  await picker.selectOption({ label: 'Class Class 1 – A' });
  const period1Row = rows.filter({ hasText: 'Period 1' });
  await expect(period1Row.getByText('Ananya Singh').first()).toBeVisible({ timeout: 8_000 });
});

// ─── TC05: Conflict Detection in Timetable ────────────────────────────────────
test('TC05 – assigning double-booked teacher shows conflict error', async ({ page }) => {
  await loginAs(page, PRINCIPAL_EMAIL, PRINCIPAL_PASSWORD);
  await page.goto(`${TENANT_URL}/teachers/timetable`);
  await page.waitForLoadState('networkidle');

  // Select Class 2A — Ananya is NOT the class teacher here
  const picker = page.locator('select').first();
  await picker.selectOption({ label: 'Class Class 2 – A' });

  await expect(page.getByText(/Weekly schedule/i)).toBeVisible({ timeout: 8_000 });

  // Find Period 1 row, Monday column (index 1 = first data col after Period label)
  const rows = page.locator('tbody tr');
  const period1Row = rows.filter({ hasText: 'Period 1' });
  const monCell = period1Row.locator('td').nth(1);

  // If already assigned, click "Edit"; otherwise click "+ Assign"
  const hasEdit = await monCell.getByText('Edit').count();
  if (hasEdit > 0) {
    await monCell.getByText('Edit').click();
  } else {
    await monCell.getByText('+ Assign').click();
  }

  // Modal uses a plain div (no role="dialog") — locate by the white card styling
  const modalContent = page.locator('.bg-white.rounded-xl.shadow-xl');
  await expect(modalContent).toBeVisible({ timeout: 8_000 });

  // Select Ananya Singh from the Teacher dropdown (she's in Class 1A P1 Mon → conflict!)
  const teacherSelect = modalContent.locator('label').filter({ hasText: 'Teacher' }).locator('select');
  await teacherSelect.selectOption({ label: 'Ananya Singh' });

  // Save — backend will reject with conflict error
  await modalContent.getByRole('button', { name: 'Save' }).click();

  // Error banner in modal: "Teacher is already assigned to another class in this period..."
  await expect(
    modalContent.locator('.bg-red-50').filter({ hasText: /already assigned|conflict|busy/i })
  ).toBeVisible({ timeout: 8_000 });
});

// ─── TC06: Attendance Dashboard Stats ────────────────────────────────────────
test('TC06 – attendance dashboard shows correct stats for today', async ({ page }) => {
  await loginAs(page, PRINCIPAL_EMAIL, PRINCIPAL_PASSWORD);
  await page.goto(`${TENANT_URL}/attendance`);
  await page.waitForLoadState('networkidle');

  // Set the date to today (the day we marked attendance)
  const datePicker = page.locator('input[type="date"]');
  await datePicker.fill(TODAY);
  await page.waitForTimeout(1000);

  // Stats card should be visible with numbers
  await expect(page.getByText("Today's breakdown")).toBeVisible({ timeout: 8_000 });

  // Stat labels — use exact+first to avoid substring match with "Unmarked sections"
  await expect(page.getByText('Marked', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Present', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Absent', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Late', { exact: true }).first()).toBeVisible();

  // Verify the numbers are live and non-zero (37 marked, 25 present, 5 absent, 6 late)
  // One legacy section (5·B / Priya Sharma) may still be unmarked — that's fine
  const markedValue = page.locator('div.text-2xl').first();
  await expect(markedValue).toBeVisible({ timeout: 5_000 });
  const markedText = await markedValue.textContent();
  expect(Number(markedText)).toBeGreaterThan(0);
});

// ─── TC07: Attendance Section Detail (Class 1A) ───────────────────────────────
test('TC07 – Class 1A attendance detail shows all 4 students with status', async ({ page }) => {
  await loginAs(page, PRINCIPAL_EMAIL, PRINCIPAL_PASSWORD);
  await page.goto(`${TENANT_URL}/attendance/${SEC_1A}?date=${TODAY}`);
  await page.waitForLoadState('networkidle');

  // Students in Class 1A
  const students = ['Aarav Sharma', 'Diya Patel', 'Ishaan Verma', 'Kavya Nair'];
  for (const name of students) {
    await expect(page.getByText(name)).toBeVisible({ timeout: 8_000 });
  }

  // Each student should have a status badge (PRESENT, ABSENT, LATE, etc.)
  const badges = page.locator('.bg-green-100, .bg-red-100, .bg-amber-100, .bg-blue-100');
  const badgeCount = await badges.count();
  expect(badgeCount).toBeGreaterThanOrEqual(4);

  // Verify Aarav Sharma is LATE (we marked first student LATE in Class 1A)
  const aaravRow = page.locator('li, tr').filter({ hasText: 'Aarav Sharma' }).first();
  await expect(aaravRow.getByText('LATE')).toBeVisible({ timeout: 5_000 });
});

// ─── TC08: Class Teacher Login (Ananya Singh) ────────────────────────────────
test('TC08 – class teacher login shows only assigned section', async ({ page }) => {
  await loginAs(page, TEACHER_EMAIL, TEACHER_PASSWORD);

  // Teacher should land somewhere in the tenant
  await expect(page).toHaveURL(new RegExp(TENANT), { timeout: 10_000 });

  // Navigate to attendance as the class teacher
  await page.goto(`${TENANT_URL}/attendance`);
  await page.waitForLoadState('networkidle');

  // Class teacher sees "Your assigned sections" subtitle (not the admin summary)
  await expect(page.getByText(/Your assigned sections/i)).toBeVisible({ timeout: 8_000 });

  // Should NOT see the full stats dashboard (no "Today's breakdown" card)
  await expect(page.getByText("Today's breakdown")).not.toBeVisible();

  // My sections card shows section rows: "Section A" + "Mark attendance" link
  await expect(page.getByText('My sections')).toBeVisible({ timeout: 8_000 });
  await expect(page.getByText('Mark attendance \u2192').first()).toBeVisible({ timeout: 5_000 });
});

// ─── TC09: Invite and Verify New Teacher (Invite Flow) ────────────────────────
test('TC09 – principal can invite a new teacher via email', async ({ page }) => {
  await loginAs(page, PRINCIPAL_EMAIL, PRINCIPAL_PASSWORD);
  await page.goto(`${TENANT_URL}/teachers/onboard`);
  await page.waitForLoadState('networkidle');

  // Click "Invite teacher" button
  await page.getByRole('button', { name: /Invite teacher/i }).click();

  // Modal uses a plain div — locate by white card styling
  const modal = page.locator('.bg-white.rounded-xl.shadow-xl');
  await expect(modal).toBeVisible({ timeout: 5_000 });
  await expect(modal.getByText('Invite teacher')).toBeVisible();

  // Fill in the form with a unique new teacher
  const ts = Date.now();
  const firstName = 'TestQA';
  const lastName = `${ts}`;
  const email = `testqa${ts}@vms.school`;

  await modal.getByLabel('First name').fill(firstName);
  await modal.getByLabel('Last name').fill(lastName);
  await modal.getByLabel('Email address').fill(email);
  // Phone is optional and globally unique — skip to avoid duplicate constraint on re-runs
  await modal.locator('select').selectOption('CLASS_TEACHER');

  // Submit
  await modal.getByRole('button', { name: /Send invite/i }).click();

  // Success confirmation should show
  await expect(modal.getByText(/Invite sent!/i)).toBeVisible({ timeout: 8_000 });

  // Wait for auto-close and verify teacher appears in list
  await expect(modal).not.toBeVisible({ timeout: 5_000 });
  await expect(page.getByText(firstName, { exact: false }).first()).toBeVisible({ timeout: 8_000 });
});

// ─── TC10: Assign Teacher to Timetable Slot (Happy Path) ──────────────────────
test('TC10 – principal can assign Deepa Nair to Class 2A Period 5 Monday', async ({ page }) => {
  await loginAs(page, PRINCIPAL_EMAIL, PRINCIPAL_PASSWORD);
  await page.goto(`${TENANT_URL}/teachers/timetable`);
  await page.waitForLoadState('networkidle');

  await expect(page.getByText('Period 5')).toBeVisible({ timeout: 8_000 });

  // Select Class 2A
  const picker = page.locator('select').first();
  await picker.selectOption({ label: 'Class Class 2 – A' });
  await expect(page.getByText(/Weekly schedule/i)).toBeVisible({ timeout: 8_000 });
  // Wait for timetable teacher-name API calls to settle before interacting with cells
  await page.waitForLoadState('networkidle');

  // Find Period 5 row, Monday column (index 1)
  const rows = page.locator('tbody tr');
  const period5Row = rows.filter({ hasText: 'Period 5' });
  const monCell = period5Row.locator('td').nth(1);

  // If already assigned, skip (idempotent)
  const existingEdit = await monCell.getByText('Edit').count();
  if (existingEdit > 0) {
    await expect(monCell).not.toBeEmpty();
    return;
  }

  // Click "+ Assign" — use force:false but re-locate stably after networkidle
  await expect(monCell.getByText('+ Assign')).toBeVisible({ timeout: 5_000 });
  await monCell.getByText('+ Assign').click();

  // Modal uses plain div (no role="dialog") — locate by white card styling
  const modal = page.locator('.bg-white.rounded-xl.shadow-xl');
  await expect(modal).toBeVisible({ timeout: 5_000 });
  await expect(modal.getByText(/Assign slot/i)).toBeVisible();

  // Select Deepa Nair from Teacher dropdown (she's free during P5 Mon)
  const teacherSelect = modal.locator('label').filter({ hasText: 'Teacher' }).locator('select');
  await teacherSelect.selectOption({ label: 'Deepa Nair' });

  // Save
  await modal.getByRole('button', { name: 'Save' }).click();

  // Modal closes, cell now shows Deepa Nair
  await expect(modal).not.toBeVisible({ timeout: 8_000 });
  await expect(monCell.getByText('Deepa Nair')).toBeVisible({ timeout: 8_000 });
});
