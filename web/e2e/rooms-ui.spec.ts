/**
 * E2E (real browser + real backend): Rooms management under Academic Planning.
 * Renders the rooms list (incl. the Lab-1 added during API verification) and adds a room.
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
  await page.goto(`/tenants/${TENANT}/academic-planning/rooms`);
  await page.waitForLoadState('networkidle');
}

test('rooms page renders and lists existing rooms', async ({ page }) => {
  await open(page);
  await expect(page.getByRole('heading', { name: /Rooms & Classrooms/i })).toBeVisible();
  await expect(page.getByText('Add a room')).toBeVisible();
  await expect(page.getByText('Lab-1')).toBeVisible();   // created during API verification
});

test('admin can add a room via the UI', async ({ page }) => {
  await open(page);
  const name = `Room ${Date.now()}`;
  await page.getByPlaceholder('e.g. Room 8A').fill(name);
  await page.getByRole('button', { name: /^Add$/ }).click();
  await expect(page.getByText(name)).toBeVisible({ timeout: 10_000 });
});
