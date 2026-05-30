/**
 * Visual Release Snapshots
 *
 * Captures full-page screenshots of every major workflow for every role.
 * Browse results in: web/test-results/snapshots/
 *
 * Run:
 *   cd web && npx playwright test e2e/visual-release-snapshots.spec.ts --reporter=list
 *
 * Roles covered:
 *   PRINCIPAL     → dashboard, students, library/books, library/issues, ptm, schedule, circulars, onboard
 *   CLASS_TEACHER → dashboard, schedule, attendance, library/books
 *   LIBRARIAN     → library/books, library/issues, schedule
 *   ACCOUNTANT    → fees/collect, fees/dashboard, library/books (no add-book)
 */

import { test, expect, type Page } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

// ─── Setup ────────────────────────────────────────────────────────────────────
const TENANT     = '926c372c-139d-460d-83b1-1a80ef92db57';
const BASE_URL   = 'http://localhost:3001';
const TENANT_URL = `${BASE_URL}/tenants/${TENANT}`;

const PRINCIPAL  = { email: 'teacher@vms.school',      password: 'Test@1234'   };
const TEACHER    = { email: 'ananya.singh@vms.school', password: 'Teacher@123' };
const LIBRARIAN  = { email: 'librarian@vms.school',    password: 'Teacher@123' };
const ACCOUNTANT = { email: 'accountant@vms.school',   password: 'Teacher@123' };

const SNAP_DIR = path.join(__dirname, '..', 'test-results', 'snapshots');

// Ensure output directory exists before any test runs
fs.mkdirSync(SNAP_DIR, { recursive: true });

// ─── Helpers ──────────────────────────────────────────────────────────────────
async function loginAs(page: Page, creds: { email: string; password: string }) {
  await page.goto(`${BASE_URL}/login`);
  await page.waitForLoadState('networkidle');
  const emailBtn = page.getByRole('button', { name: /^Email$/i });
  if (await emailBtn.count() > 0) await emailBtn.click();
  await page.locator('input[type="email"]').fill(creds.email);
  await page.locator('input[type="password"]').fill(creds.password);
  await page.getByRole('button', { name: /^Sign in$/i }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 20_000 });
}

async function snap(page: Page, role: string, label: string) {
  // Brief wait for animations/data to settle
  await page.waitForTimeout(800);
  const filename = `${role}__${label}.png`.replace(/[^a-z0-9_\-.]/gi, '_');
  const filepath = path.join(SNAP_DIR, filename);
  await page.screenshot({ path: filepath, fullPage: true });
  console.log(`📸  Saved: test-results/snapshots/${filename}`);
}

// ══════════════════════════════════════════════════════════════════════════════
//  PRINCIPAL (Rajesh Kumar) — full admin access
// ══════════════════════════════════════════════════════════════════════════════
test.describe('PRINCIPAL snapshots', () => {
  test.setTimeout(120_000);

  test('principal – dashboard', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.waitForLoadState('networkidle');
    await snap(page, 'principal', '01-dashboard');
  });

  test('principal – students list', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/students`);
    await expect(page.getByText('Aarav Sharma')).toBeVisible({ timeout: 15_000 });
    await snap(page, 'principal', '02-students');
  });

  test('principal – library books catalogue', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/library/books`);
    await expect(page.getByText('The Jungle Book')).toBeVisible({ timeout: 15_000 });
    await snap(page, 'principal', '03-library-books');
  });

  test('principal – add book modal open', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/library/books`);
    await expect(page.getByRole('button', { name: /Add book/i })).toBeVisible({ timeout: 10_000 });
    await page.getByRole('button', { name: /Add book/i }).click();
    const modal = page.locator('.bg-white.rounded-xl.shadow-xl, [role="dialog"]').first();
    await expect(modal).toBeVisible({ timeout: 5_000 });
    await snap(page, 'principal', '04-library-add-book-modal');
  });

  test('principal – library issues (active loans)', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/library/issues`);
    await expect(page.getByText('Aarav Sharma')).toBeVisible({ timeout: 15_000 });
    await snap(page, 'principal', '05-library-issues');
  });

  test('principal – issue book modal open', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/library/issues`);
    await page.getByRole('button', { name: /Issue book/i }).first().click();
    const modal = page.locator('.bg-white.rounded-xl.shadow-xl, [role="dialog"]').first();
    await expect(modal).toBeVisible({ timeout: 5_000 });
    await snap(page, 'principal', '06-library-issue-book-modal');
  });

  test('principal – PTM schedule', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/ptm`);
    await page.waitForLoadState('networkidle');
    await snap(page, 'principal', '07-ptm-page');
  });

  test('principal – PTM create slot modal', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/ptm`);
    await page.getByRole('button', { name: /Create slot/i }).click();
    const modal = page.locator('.bg-white.rounded-xl.shadow-xl, [role="dialog"]').first();
    await expect(modal).toBeVisible({ timeout: 5_000 });
    await snap(page, 'principal', '08-ptm-create-slot-modal');
  });

  test('principal – timetable / schedule', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/schedule`);
    await page.waitForLoadState('networkidle');
    await snap(page, 'principal', '09-schedule');
  });

  test('principal – assign substitute dialog', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/schedule`);
    await expect(page.getByRole('button', { name: /Assign substitute/i })).toBeVisible({ timeout: 10_000 });
    await page.getByRole('button', { name: /Assign substitute/i }).click();
    const modal = page.locator('.bg-white.rounded-xl.shadow-xl, [role="dialog"]').first();
    await expect(modal).toBeVisible({ timeout: 5_000 });
    await snap(page, 'principal', '10-assign-substitute-dialog');
  });

  test('principal – circulars', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/circulars`);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2_000);
    await snap(page, 'principal', '11-circulars');
  });

  test('principal – compose circular modal', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/circulars`);
    await expect(page.getByRole('button', { name: /Compose/i })).toBeVisible({ timeout: 10_000 });
    await page.getByRole('button', { name: /Compose/i }).click();
    const modal = page.locator('.bg-white.rounded-xl.shadow-xl, [role="dialog"]').first();
    await expect(modal).toBeVisible({ timeout: 5_000 });
    await snap(page, 'principal', '12-compose-circular-modal');
  });

  test('principal – teachers onboard (invite)', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/teachers/onboard`);
    await page.waitForLoadState('networkidle');
    await snap(page, 'principal', '13-teachers-onboard');
  });

  test('principal – attendance overview', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/attendance`);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2_000);
    await snap(page, 'principal', '14-attendance');
  });
});

// ══════════════════════════════════════════════════════════════════════════════
//  CLASS_TEACHER (Ananya Singh) — teacher-limited access
// ══════════════════════════════════════════════════════════════════════════════
test.describe('CLASS_TEACHER snapshots', () => {
  test.setTimeout(90_000);

  test('class teacher – dashboard', async ({ page }) => {
    await loginAs(page, TEACHER);
    await page.waitForLoadState('networkidle');
    await snap(page, 'class_teacher', '01-dashboard');
  });

  test('class teacher – my schedule (timetable)', async ({ page }) => {
    await loginAs(page, TEACHER);
    await page.goto(`${TENANT_URL}/schedule`);
    await page.waitForLoadState('networkidle');
    await snap(page, 'class_teacher', '02-schedule');
  });

  test('class teacher – attendance', async ({ page }) => {
    await loginAs(page, TEACHER);
    await page.goto(`${TENANT_URL}/attendance`);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2_000);
    await snap(page, 'class_teacher', '03-attendance');
  });

  test('class teacher – library books (read only)', async ({ page }) => {
    await loginAs(page, TEACHER);
    await page.goto(`${TENANT_URL}/library/books`);
    await expect(page.getByText('The Jungle Book')).toBeVisible({ timeout: 15_000 });
    await snap(page, 'class_teacher', '04-library-books-readonly');
  });

  test('class teacher – library issues (read only)', async ({ page }) => {
    await loginAs(page, TEACHER);
    await page.goto(`${TENANT_URL}/library/issues`);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2_000);
    await snap(page, 'class_teacher', '05-library-issues-readonly');
  });
});

// ══════════════════════════════════════════════════════════════════════════════
//  LIBRARIAN (Vikram Das) — library-focused access
// ══════════════════════════════════════════════════════════════════════════════
test.describe('LIBRARIAN snapshots', () => {
  test.setTimeout(90_000);

  test('librarian – dashboard / landing', async ({ page }) => {
    await loginAs(page, LIBRARIAN);
    await page.waitForLoadState('networkidle');
    await snap(page, 'librarian', '01-landing');
  });

  test('librarian – library books (with Add book button)', async ({ page }) => {
    await loginAs(page, LIBRARIAN);
    await page.goto(`${TENANT_URL}/library/books`);
    await expect(page.getByText('The Jungle Book')).toBeVisible({ timeout: 15_000 });
    await snap(page, 'librarian', '02-library-books');
  });

  test('librarian – add book modal', async ({ page }) => {
    await loginAs(page, LIBRARIAN);
    await page.goto(`${TENANT_URL}/library/books`);
    await expect(page.getByRole('button', { name: /Add book/i })).toBeVisible({ timeout: 10_000 });
    await page.getByRole('button', { name: /Add book/i }).click();
    const modal = page.locator('.bg-white.rounded-xl.shadow-xl, [role="dialog"]').first();
    await expect(modal).toBeVisible({ timeout: 5_000 });
    await snap(page, 'librarian', '03-add-book-modal');
  });

  test('librarian – library issues (with Issue book button)', async ({ page }) => {
    await loginAs(page, LIBRARIAN);
    await page.goto(`${TENANT_URL}/library/issues`);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2_000);
    await snap(page, 'librarian', '04-library-issues');
  });

  test('librarian – issue book modal', async ({ page }) => {
    await loginAs(page, LIBRARIAN);
    await page.goto(`${TENANT_URL}/library/issues`);
    await expect(page.getByRole('button', { name: /Issue book/i })).toBeVisible({ timeout: 10_000 });
    await page.getByRole('button', { name: /Issue book/i }).first().click();
    const modal = page.locator('.bg-white.rounded-xl.shadow-xl, [role="dialog"]').first();
    await expect(modal).toBeVisible({ timeout: 5_000 });
    await snap(page, 'librarian', '05-issue-book-modal');
  });

  test('librarian – my schedule', async ({ page }) => {
    await loginAs(page, LIBRARIAN);
    await page.goto(`${TENANT_URL}/schedule`);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2_000);
    await snap(page, 'librarian', '06-my-schedule');
  });
});

// ══════════════════════════════════════════════════════════════════════════════
//  ACCOUNTANT (Sunita Rao) — fee-focused access
// ══════════════════════════════════════════════════════════════════════════════
test.describe('ACCOUNTANT snapshots', () => {
  test.setTimeout(90_000);

  test('accountant – dashboard / landing', async ({ page }) => {
    await loginAs(page, ACCOUNTANT);
    await page.waitForLoadState('networkidle');
    await snap(page, 'accountant', '01-landing');
  });

  test('accountant – fee dashboard', async ({ page }) => {
    await loginAs(page, ACCOUNTANT);
    await page.goto(`${TENANT_URL}/fees/dashboard`);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2_000);
    await snap(page, 'accountant', '02-fee-dashboard');
  });

  test('accountant – fee collect page', async ({ page }) => {
    await loginAs(page, ACCOUNTANT);
    await page.goto(`${TENANT_URL}/fees/collect`);
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('input[placeholder*="Search by name"]')).toBeVisible({ timeout: 15_000 });
    await snap(page, 'accountant', '03-fee-collect');
  });

  test('accountant – fee collect: student search results', async ({ page }) => {
    await loginAs(page, ACCOUNTANT);
    await page.goto(`${TENANT_URL}/fees/collect`);
    await page.waitForLoadState('domcontentloaded');
    const searchInput = page.locator('input[placeholder*="Search by name"]').first();
    await expect(searchInput).toBeVisible({ timeout: 15_000 });
    await searchInput.fill('Aarav');
    await page.waitForTimeout(1_500);
    await snap(page, 'accountant', '04-fee-collect-search-results');
  });

  test('accountant – library books (no Add book button)', async ({ page }) => {
    await loginAs(page, ACCOUNTANT);
    await page.goto(`${TENANT_URL}/library/books`);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2_000);
    await snap(page, 'accountant', '05-library-books-no-write');
  });
});
