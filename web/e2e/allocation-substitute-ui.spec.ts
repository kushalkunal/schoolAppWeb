/**
 * E2E (real browser, real backend): UI walkthrough of the two modules —
 *   1. Teacher Class Allocations  → Teachers ▸ Command Center (+ Classes page)
 *   2. Substitute Allocations     → Schedule ▸ Assign substitute (availability-aware picker)
 *
 * Logs in for real (API → real JWT seeded into localStorage under the app's sms.* keys), then
 * navigates the actual pages and asserts what an admin sees. Requires backend :8081 and web :3001.
 */

import { test, expect, type Page } from '@playwright/test';

const API_BASE = process.env.E2E_API_BASE ?? 'http://localhost:8081';
const TENANT = '926c372c-139d-460d-83b1-1a80ef92db57';
const PRINCIPAL = { email: 'teacher@vms.school', password: 'Test@1234' };

let auth: { accessToken: string; refreshToken: string; expiresAt: number };

test.beforeAll(async ({ playwright }) => {
  const req = await playwright.request.newContext();
  const res = await req.post(`${API_BASE}/api/v1/auth/password/login`, { data: PRINCIPAL });
  expect(res.ok(), `login: ${res.status()}`).toBeTruthy();
  const d = (await res.json()).data;
  auth = {
    accessToken: d.accessToken,
    refreshToken: d.refreshToken ?? 'r',
    expiresAt: Date.now() + (d.expiresInSeconds ?? 3600) * 1000,
  };
  await req.dispose();
});

// Seed the real token before the SPA boots so it hydrates authenticated against the live API.
test.beforeEach(async ({ page }) => {
  await page.addInitScript((a) => {
    localStorage.setItem('sms.accessToken', a.accessToken);
    localStorage.setItem('sms.refreshToken', a.refreshToken);
    localStorage.setItem('sms.expiresAt', String(a.expiresAt));
  }, auth);
});

async function goto(page: Page, path: string) {
  await page.goto(path);
  await page.waitForLoadState('networkidle');
}

// ─── Module 1: Teacher Class Allocations (Command Center) ──────────────────────
test.describe('Module 1 — Teacher Class Allocations', () => {
  test('Command Center loads with KPIs, allocation matrix and tabs', async ({ page }) => {
    await goto(page, `/tenants/${TENANT}/teachers/allocation`);

    await expect(page.getByRole('heading', { name: /Teacher Allocation Command Center/i })).toBeVisible();
    // KPI cards (use labels unique to the KPI grid to avoid the sub-nav "Classes" link)
    await expect(page.getByText('Allocation gaps', { exact: true })).toBeVisible();
    await expect(page.getByText('Periods / day', { exact: true })).toBeVisible();
    // Matrix tab content: at least one class card + a "Class Teacher" label or "No class teacher" badge
    await expect(page.getByText(/Class Teacher|No class teacher/i).first()).toBeVisible();
  });

  test('Workload tab shows per-teacher utilization', async ({ page }) => {
    await goto(page, `/tenants/${TENANT}/teachers/allocation`);
    await page.getByRole('button', { name: 'Teacher Workload' }).click();
    await expect(page.getByText('Periods/wk')).toBeVisible();
    await expect(page.getByText('Utilization')).toBeVisible();
    // a percentage badge appears in the table
    await expect(page.getByText(/\d+%/).first()).toBeVisible();
  });

  test('Today/Now tab renders the live teaching panel', async ({ page }) => {
    await goto(page, `/tenants/${TENANT}/teachers/allocation`);
    await page.getByRole('button', { name: 'Today / Now' }).click();
    await expect(page.getByText(/Currently teaching/i)).toBeVisible();
  });

  test('Classes page shows class-teacher allocation', async ({ page }) => {
    await goto(page, `/tenants/${TENANT}/teachers/classes`);
    // Either the class-teacher management UI or the consolidated allocation view renders
    await expect(page.getByText(/Class Teacher|Subject/i).first()).toBeVisible();
  });
});

// ─── Module 2: Substitute Allocations (availability-aware) ─────────────────────
test.describe('Module 2 — Substitute Allocations', () => {
  test('Assign-substitute modal offers an availability-aware picker (Free this period)', async ({ page }) => {
    await goto(page, `/tenants/${TENANT}/schedule`);

    // Admin sees the Assign substitute action.
    const openBtn = page.getByRole('button', { name: /Assign substitute/i }).first();
    await expect(openBtn).toBeVisible();
    await openBtn.click();

    // The dialog renders a form with selects: [0]=section, [1]=period, [2]=absent, [3]=substitute.
    const form = page.locator('form').last();
    await form.locator('select').nth(0).selectOption({ index: 1 });   // a section
    await form.locator('select').nth(1).selectOption({ index: 1 });   // first real period

    // Candidates load → the substitute picker gains an availability-aware "Free this period" group.
    // (optgroup label is an attribute, so assert on the element, not visible text.)
    await expect(page.locator('optgroup[label*="Free this period"]')).toHaveCount(1, { timeout: 15_000 });
  });
});
