/**
 * E2E (real browser + real backend): School Calendar settings (Settings ▸ Calendar).
 * Working-days selector + holidays list render; an added holiday shows up. Admin-only.
 */

import { test, expect, type Page } from '@playwright/test';

const API_BASE = process.env.E2E_API_BASE ?? 'http://localhost:8081';
const TENANT = '926c372c-139d-460d-83b1-1a80ef92db57';

let auth: any;
test.beforeAll(async ({ playwright }) => {
  const req = await playwright.request.newContext();
  const res = await req.post(`${API_BASE}/api/v1/auth/password/login`,
    { data: { email: 'teacher@vms.school', password: 'Test@1234' } });
  const d = (await res.json()).data;
  auth = { accessToken: d.accessToken, refreshToken: d.refreshToken ?? 'r', expiresAt: Date.now() + 3600_000 };
  await req.dispose();
});

async function open(page: Page) {
  await page.addInitScript((a) => {
    localStorage.setItem('sms.accessToken', a.accessToken);
    localStorage.setItem('sms.refreshToken', a.refreshToken);
    localStorage.setItem('sms.expiresAt', String(a.expiresAt));
  }, auth);
  await page.goto(`/tenants/${TENANT}/settings/calendar`);
  await page.waitForLoadState('networkidle');
}

test('calendar settings render working days + holidays', async ({ page }) => {
  await open(page);
  await expect(page.getByRole('heading', { name: /School Calendar/i })).toBeVisible();
  await expect(page.getByText('Working Days').first()).toBeVisible();
  // weekday toggles
  for (const d of ['Mon', 'Tue', 'Sat', 'Sun']) {
    await expect(page.getByRole('button', { name: d, exact: true })).toBeVisible();
  }
  // holidays section + the seeded Independence Day row (added during API verification)
  await expect(page.getByText('Holidays & Events')).toBeVisible();
  await expect(page.getByText('Independence Day')).toBeVisible();
});

test('admin can add a holiday via the UI', async ({ page }) => {
  await open(page);
  const name = `QA Event ${Date.now()}`;
  await page.locator('input[type="date"]').fill('2026-12-25');
  await page.getByPlaceholder(/Diwali, Sports Day/i).fill(name);
  await page.getByRole('button', { name: /^Add$/ }).click();
  await expect(page.getByText(name)).toBeVisible({ timeout: 10_000 });
});
