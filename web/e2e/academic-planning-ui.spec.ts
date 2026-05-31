/**
 * E2E (real browser + real backend): the dedicated "Academic Planning" module.
 *
 *  - Sidebar entry navigates into the module.
 *  - Live Teaching Monitor renders its real-time widgets (teaching now / free / upcoming / on leave).
 *  - Sub-nav consolidates the existing screens (allocations, subjects, timetable, replacement).
 *  - RBAC: a teacher cannot open the module.
 *
 * Requires backend :8081 and web :3001. Logs in for real (JWT seeded into the app's sms.* keys).
 */

import { test, expect, type Page } from '@playwright/test';

const API_BASE = process.env.E2E_API_BASE ?? 'http://localhost:8081';
const TENANT = '926c372c-139d-460d-83b1-1a80ef92db57';
const PRINCIPAL = { email: 'teacher@vms.school', password: 'Test@1234' };
const TEACHER = { email: 'ananya.singh@vms.school', password: 'Teacher@123' };
const ROOT = `/tenants/${TENANT}/academic-planning`;

async function tokenFor(playwright: any, creds: { email: string; password: string }) {
  const req = await playwright.request.newContext();
  const res = await req.post(`${API_BASE}/api/v1/auth/password/login`, { data: creds });
  expect(res.ok(), `login ${creds.email}: ${res.status()}`).toBeTruthy();
  const d = (await res.json()).data;
  await req.dispose();
  return { accessToken: d.accessToken, refreshToken: d.refreshToken ?? 'r', expiresAt: Date.now() + (d.expiresInSeconds ?? 3600) * 1000 };
}

let principal: any;
let teacher: any;
test.beforeAll(async ({ playwright }) => {
  principal = await tokenFor(playwright, PRINCIPAL);
  teacher = await tokenFor(playwright, TEACHER);
});

async function seed(page: Page, auth: any) {
  await page.addInitScript((a) => {
    localStorage.setItem('sms.accessToken', a.accessToken);
    localStorage.setItem('sms.refreshToken', a.refreshToken);
    localStorage.setItem('sms.expiresAt', String(a.expiresAt));
  }, auth);
}
async function goto(page: Page, path: string) {
  await page.goto(path);
  await page.waitForLoadState('networkidle');
}

test.describe('Academic Planning module (admin)', () => {
  test.beforeEach(async ({ page }) => seed(page, principal));

  test('sidebar entry opens the module on the Live Monitor', async ({ page }) => {
    await goto(page, `/tenants/${TENANT}/dashboard`);
    await page.getByRole('link', { name: 'Academic Planning' }).first().click();
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveURL(/academic-planning\/monitor/);
    await expect(page.getByRole('heading', { name: /Live Teaching Monitor/i })).toBeVisible();
  });

  test('Live Monitor shows the real-time widgets', async ({ page }) => {
    await goto(page, `${ROOT}/monitor`);
    await expect(page.getByRole('heading', { name: /Live Teaching Monitor/i })).toBeVisible();
    // KPI strip
    await expect(page.getByText('Teaching Now', { exact: true })).toBeVisible();
    await expect(page.getByText('Free Now', { exact: true })).toBeVisible();
    await expect(page.getByText('On Leave Today', { exact: true })).toBeVisible();
    // The four live cards
    await expect(page.getByText('Currently Teaching').first()).toBeVisible();
    await expect(page.getByText('Currently Free Teachers').first()).toBeVisible();
    await expect(page.getByText('Upcoming Classes').first()).toBeVisible();
    await expect(page.getByText('Teachers On Leave').first()).toBeVisible();
  });

  test('sub-nav consolidates allocations, timetable and replacement', async ({ page }) => {
    await goto(page, `${ROOT}/monitor`);

    await page.getByRole('link', { name: 'Teacher Allocations' }).click();
    await expect(page.getByRole('heading', { name: /Teacher Allocation Command Center/i })).toBeVisible();

    await page.getByRole('link', { name: 'Timetable' }).click();
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveURL(/academic-planning\/timetable/);

    await page.getByRole('link', { name: 'Replacement' }).click();
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveURL(/academic-planning\/replacement/);
  });
});

test.describe('Academic Planning RBAC', () => {
  test('a teacher cannot open the module', async ({ page }) => {
    await seed(page, teacher);
    // Avoid networkidle — the dashboard the teacher is redirected to polls continuously.
    await page.goto(`${ROOT}/monitor`, { waitUntil: 'domcontentloaded' });
    // RequireRole redirects non-admins away; the monitor heading must never render.
    await expect(page.getByRole('heading', { name: /Live Teaching Monitor/i })).toHaveCount(0, { timeout: 15_000 });
  });
});
