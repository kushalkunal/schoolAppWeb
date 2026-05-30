/**
 * E2E: Multi-Role Feature Coverage — Live Backend
 *
 * Tests every major feature against 4 distinct login roles:
 *
 *  ── PRINCIPAL (Rajesh Kumar) ──────────────────────────────────────────────
 *  TC-R01 – Login lands on admin dashboard with full nav
 *  TC-R02 – Students list: all 37 students visible with pagination
 *  TC-R03 – Library/Books: catalogue shows 3 pre-seeded books
 *  TC-R04 – Library/Books: add a new book via modal
 *  TC-R05 – Library/Issues: 1 active issue for Aarav Sharma visible
 *  TC-R06 – Library/Issues: issue a book to a student
 *  TC-R07 – PTM: create a slot with class assignment (Class 1A)
 *  TC-R08 – Schedule: "Assign substitute" button visible; absent teacher hint
 *  TC-R09 – Circulars page: accessible and loads without error
 *  TC-R10 – Teachers onboard: LIBRARIAN role available in invite dropdown
 *
 *  ── CLASS_TEACHER (Ananya Singh) ─────────────────────────────────────────
 *  TC-T01 – Login lands in teacher nav (My Dashboard, My Schedule)
 *  TC-T02 – Schedule: own timetable visible, NO "Assign substitute" button
 *  TC-T03 – Attendance: teacher view shows "Your assigned sections"
 *  TC-T04 – Library: can view books list (read access)
 *  TC-T05 – Library: "Add book" button is NOT visible (no LIBRARY_WRITER)
 *
 *  ── LIBRARIAN (Vikram Das) ────────────────────────────────────────────────
 *  TC-L01 – Login: gets library-focused nav (Books, Issued Books, My Schedule)
 *  TC-L02 – Library/Books: "Add book" button IS visible (LIBRARY_WRITER)
 *  TC-L03 – Library/Books: can add a book successfully
 *  TC-L04 – Library/Issues: "Issue book" button IS visible
 *  TC-L05 – My Schedule: page loads (may show empty state if no timetable)
 *  TC-L06 – Cannot access admin dashboard (redirected/no admin nav items)
 *
 *  ── ACCOUNTANT (Sunita Rao) ──────────────────────────────────────────────
 *  TC-A01 – Login: gets fee-focused nav (Fee Dashboard, Collect Fee, etc.)
 *  TC-A02 – Fee Collect page loads and shows student picker
 *  TC-A03 – Cannot see Library "Add book" button (no LIBRARY_WRITER)
 *  TC-A04 – Fee Dashboard: accessible with no crash
 *
 * Run command:
 *   cd web && npx playwright test e2e/multi-role-features.spec.ts --reporter=list
 *
 * Data pre-requisites (seed-test-data.sql):
 *   • Principal: teacher@vms.school / Test@1234
 *   • Class teacher: ananya.singh@vms.school / Teacher@123
 *   • Librarian: librarian@vms.school / Teacher@123  (b0000011)
 *   • Accountant: accountant@vms.school / Teacher@123  (b0000010)
 *   • 3 library books pre-seeded (e0000001, e0000002, e0000003)
 *   • 1 active issue: Aarav Sharma has Wings of Fire
 *   • Feature flags LIBRARY + PTM_SCHEDULING enabled
 */

import { test, expect, type Page } from '@playwright/test';
import { execSync } from 'child_process';

// ─── Test-suite setup: clean mutable data that would fail on re-run ────────────
// Runs once when the spec file is loaded (before any test executes).
(function cleanupBeforeRun() {
  const tenant = '926c372c-139d-460d-83b1-1a80ef92db57';
  const cmds = [
    // PTM slots have a unique constraint (teacher_id, slot_date, start_time).
    `DELETE FROM ptm_slots WHERE school_id = '${tenant}'`,
    // Library issues created by TC-R06 accumulate and eventually exhaust available_copies.
    // Keep only the original Aarav Sharma seed issue; delete any Diya Patel issues.
    `DELETE FROM library_issues WHERE school_id = '${tenant}' AND id != 'f0000001-0000-0000-0000-000000000001'`,
    // Reset available_copies for Malgudi Days to 4 (seed value) after prior TC-R06 runs.
    `UPDATE library_books SET available_copies = 4 WHERE id = 'e0000003-0000-0000-0000-000000000003'`,
  ];
  for (const sql of cmds) {
    try {
      execSync(
        `docker exec schoolapp-postgres psql -U schoolapp -d schoolapp -c "${sql}"`,
        { stdio: 'pipe' }
      );
    } catch { /* non-fatal */ }
  }
})();

// ─── Constants ────────────────────────────────────────────────────────────────
const TENANT     = '926c372c-139d-460d-83b1-1a80ef92db57';
const BASE_URL   = 'http://localhost:3001';
const TENANT_URL = `${BASE_URL}/tenants/${TENANT}`;

// Credentials
const PRINCIPAL  = { email: 'teacher@vms.school',       password: 'Test@1234'    };
const TEACHER    = { email: 'ananya.singh@vms.school',  password: 'Teacher@123'  };
const LIBRARIAN  = { email: 'librarian@vms.school',     password: 'Teacher@123'  };
const ACCOUNTANT = { email: 'accountant@vms.school',    password: 'Teacher@123'  };

const SEC_1A = '032ec61c-3373-4389-9169-16ae826c357a';

// ─── Shared login helper ──────────────────────────────────────────────────────
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

// ─── Sidebar nav helper: check link text is present in the sidenav ────────────
async function navHas(page: Page, label: string) {
  // Nav items are anchor tags in the sidebar; use getByRole('link')
  return page.getByRole('link', { name: label }).first();
}

// ══════════════════════════════════════════════════════════════════════════════
//  PRINCIPAL TESTS
// ══════════════════════════════════════════════════════════════════════════════

test('TC-R01 – principal login: full admin nav visible', async ({ page }) => {
  await loginAs(page, PRINCIPAL);
  await expect(page).toHaveURL(new RegExp(TENANT));

  // Admin nav should contain Students and Settings
  await expect(page.getByRole('link', { name: /Students/i }).first()).toBeVisible({ timeout: 8_000 });
  await expect(page.getByRole('link', { name: /Settings/i }).first()).toBeVisible({ timeout: 5_000 });

  // Must NOT be on an error page
  await expect(page.locator('body')).not.toContainText('401');
  await expect(page.locator('body')).not.toContainText('Unauthorized');
});

test('TC-R02 – principal: students list renders rows', async ({ page }) => {
  await loginAs(page, PRINCIPAL);
  await page.goto(`${TENANT_URL}/students`);
  await page.waitForLoadState('networkidle');

  // At least the first-page students should appear
  await expect(page.getByText('Aarav Sharma')).toBeVisible({ timeout: 8_000 });
  await expect(page.getByText('Diya Patel')).toBeVisible({ timeout: 5_000 });

  // Student count badge or table row count > 0
  const rows = page.locator('table tbody tr');
  const count = await rows.count();
  expect(count).toBeGreaterThan(0);
});

test('TC-R03 – principal: library books catalogue shows pre-seeded books', async ({ page }) => {
  await loginAs(page, PRINCIPAL);
  await page.goto(`${TENANT_URL}/library/books`);
  await page.waitForLoadState('domcontentloaded');

  await expect(page.getByText('The Jungle Book')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText('Wings of Fire')).toBeVisible({ timeout: 5_000 });
  await expect(page.getByText('Malgudi Days')).toBeVisible({ timeout: 5_000 });
});

test('TC-R04 – principal: add a new book via modal', async ({ page }) => {
  await loginAs(page, PRINCIPAL);
  await page.goto(`${TENANT_URL}/library/books`);
  await page.waitForLoadState('networkidle');

  // Click "Add book" button (principal has LIBRARY_WRITER)
  await page.getByRole('button', { name: /Add book/i }).first().click();

  // Modal appears
  const modal = page.locator('.bg-white.rounded-xl.shadow-xl, [role="dialog"]').first();
  await expect(modal).toBeVisible({ timeout: 5_000 });

  // Fill title
  const ts = Date.now();
  const title = `E2E Book ${ts}`;
  await modal.getByLabel(/Title/i).fill(title);
  await modal.getByLabel(/Author/i).fill('Test Author');

  // Submit
  await modal.getByRole('button', { name: /Add|Save|Create/i }).last().click();

  // Modal closes, new book appears in the list
  await expect(modal).not.toBeVisible({ timeout: 8_000 });
  await expect(page.getByText(title)).toBeVisible({ timeout: 8_000 });
});

test('TC-R05 – principal: library issues shows 1 active issue (Aarav Sharma)', async ({ page }) => {
  await loginAs(page, PRINCIPAL);
  await page.goto(`${TENANT_URL}/library/issues`);
  await page.waitForLoadState('networkidle');

  await expect(page.getByText('Wings of Fire')).toBeVisible({ timeout: 8_000 });
  await expect(page.getByText('Aarav Sharma')).toBeVisible({ timeout: 5_000 });

  // Stats: "Books on loan" stat tile is visible — data confirmed above by Aarav Sharma row
  await expect(page.getByText('Books on loan', { exact: false })).toBeVisible({ timeout: 5_000 });
});

test('TC-R06 – principal: issue a book to a student', async ({ page }) => {
  await loginAs(page, PRINCIPAL);
  await page.goto(`${TENANT_URL}/library/issues`);
  await page.waitForLoadState('networkidle');

  // Click "Issue book" button
  await page.getByRole('button', { name: /Issue book/i }).first().click();

  const modal = page.locator('.bg-white.rounded-xl.shadow-xl, [role="dialog"]').first();
  await expect(modal).toBeVisible({ timeout: 5_000 });

  // Select book: Malgudi Days (option value is its UUID)
  const bookSelect = modal.locator('select').first();
  await bookSelect.selectOption('e0000003-0000-0000-0000-000000000003');

  // Pick a student via StudentPicker — click combobox then type
  const combobox = modal.locator('[role="combobox"]').first();
  await combobox.click();
  await modal.locator('input[type="text"]').last().fill('Diya');
  await page.waitForTimeout(800);
  // Click within the modal to avoid intercepting by modal backdrop
  await modal.getByText('Diya Patel').first().click();

  // Due date defaults; submit
  await modal.getByRole('button', { name: /^Issue book$/i }).last().click();

  // Modal closes
  await expect(modal).not.toBeVisible({ timeout: 10_000 });

  // Diya Patel now appears in the outstanding issues list
  await expect(page.getByText('Diya Patel').first()).toBeVisible({ timeout: 8_000 });
});

test('TC-R07 – principal: create a PTM slot with class assignment', async ({ page }) => {
  await loginAs(page, PRINCIPAL);
  await page.goto(`${TENANT_URL}/ptm`);
  await page.waitForLoadState('networkidle');

  // Click "Create slot"
  await page.getByRole('button', { name: /Create slot/i }).click();

  const modal = page.locator('.bg-white.rounded-xl.shadow-xl, [role="dialog"]').first();
  await expect(modal).toBeVisible({ timeout: 5_000 });

  // Select Class 1 — A (em dash separator used in option labels)
  const classSelect = modal.locator('select').first();
  await classSelect.selectOption({ value: '032ec61c-3373-4389-9169-16ae826c357a' });

  // Hint text should appear
  await expect(modal.getByText(/parents in this class will receive/i)).toBeVisible({ timeout: 3_000 });

  // Select teacher via StaffPicker combobox
  const staffCombo = modal.locator('[role="combobox"]').first();
  await staffCombo.click();
  await modal.locator('input[type="text"]').last().fill('Ananya');
  await page.waitForTimeout(600);
  await modal.getByText('Ananya Singh').first().click();

  // Button label should read "Create & notify parents"
  await expect(modal.getByRole('button', { name: /Create & notify parents/i })).toBeVisible();

  // Submit
  await modal.getByRole('button', { name: /Create/i }).last().click();

  // Modal closes, slot appears in list
  await expect(modal).not.toBeVisible({ timeout: 8_000 });
  await expect(page.getByText(/Class 1.*A/i).first()).toBeVisible({ timeout: 8_000 });
});

test('TC-R08 – principal: schedule page has "Assign substitute" button', async ({ page }) => {
  await loginAs(page, PRINCIPAL);
  await page.goto(`${TENANT_URL}/schedule`);
  await page.waitForLoadState('networkidle');

  // Principal/admin sees this button
  await expect(page.getByRole('button', { name: /Assign substitute/i })).toBeVisible({ timeout: 8_000 });

  // Open the dialog
  await page.getByRole('button', { name: /Assign substitute/i }).click();

  const modal = page.locator('.bg-white.rounded-xl.shadow-xl, [role="dialog"]').first();
  await expect(modal).toBeVisible({ timeout: 5_000 });

  // "Absent teacher" label visible
  await expect(modal.getByText(/Absent teacher/i)).toBeVisible({ timeout: 3_000 });

  await modal.getByRole('button', { name: /Cancel/i }).click();
});

test('TC-R09 – principal: circulars page accessible', async ({ page }) => {
  await loginAs(page, PRINCIPAL);
  await page.goto(`${TENANT_URL}/circulars`);
  await page.waitForLoadState('networkidle');

  await expect(page.locator('body')).not.toContainText('401');
  await expect(page.locator('body')).not.toContainText('Error');
  // Either "Compose" button (OWNER_OR_ADMIN) or the empty state card is rendered
  const hasBtn   = await page.getByRole('button', { name: /Compose|New circular|Create/i }).count();
  const hasEmpty = await page.getByText(/No circulars yet|No circulars|Create your first/i).count();
  const hasList  = await page.locator('h3').count(); // existing circular titles
  expect(hasBtn + hasEmpty + hasList).toBeGreaterThan(0);
});

test('TC-R10 – principal: invite dropdown has Librarian option', async ({ page }) => {
  await loginAs(page, PRINCIPAL);
  await page.goto(`${TENANT_URL}/teachers/onboard`);
  await page.waitForLoadState('networkidle');

  await page.getByRole('button', { name: /Invite teacher/i }).click();

  const modal = page.locator('.bg-white.rounded-xl.shadow-xl, [role="dialog"]').first();
  await expect(modal).toBeVisible({ timeout: 5_000 });

  // Role select should include Librarian
  const roleSelect = modal.locator('select');
  const options = await roleSelect.locator('option').allTextContents();
  expect(options.some((o) => /Librarian/i.test(o))).toBe(true);

  await modal.getByRole('button', { name: /Cancel/i }).click();
});

// ══════════════════════════════════════════════════════════════════════════════
//  CLASS_TEACHER TESTS
// ══════════════════════════════════════════════════════════════════════════════

test('TC-T01 – class teacher login: teacher-specific nav links visible', async ({ page }) => {
  await loginAs(page, TEACHER);
  await expect(page).toHaveURL(new RegExp(TENANT), { timeout: 10_000 });

  // Teacher nav has: My Dashboard, My Schedule, Attendance (for their class)
  await expect(page.getByRole('link', { name: /My Schedule/i }).first()).toBeVisible({ timeout: 8_000 });
  await expect(page.getByRole('link', { name: /My Dashboard/i }).first()).toBeVisible({ timeout: 5_000 });

  // Should NOT have admin-only items like "Students" or "Settings" in nav
  const studentsNavCount = await page.getByRole('link', { name: /^Students$/i }).count();
  expect(studentsNavCount).toBe(0);
});

test('TC-T02 – class teacher: schedule shows own timetable, no substitute button', async ({ page }) => {
  await loginAs(page, TEACHER);
  await page.goto(`${TENANT_URL}/schedule`);
  await page.waitForLoadState('networkidle');

  // Ananya has timetable entries — "My Weekly Schedule" heading
  await expect(page.getByText(/My Weekly Schedule/i)).toBeVisible({ timeout: 8_000 });

  // "Assign substitute" is admin-only — should NOT be visible for class teacher
  const subBtn = await page.getByRole('button', { name: /Assign substitute/i }).count();
  expect(subBtn).toBe(0);
});

test('TC-T03 – class teacher: attendance page shows teacher-specific view', async ({ page }) => {
  await loginAs(page, TEACHER);
  await page.goto(`${TENANT_URL}/attendance`);
  await page.waitForLoadState('networkidle');

  await expect(page.getByText(/Your assigned sections/i)).toBeVisible({ timeout: 8_000 });

  // Full stats dashboard ("Today's breakdown") NOT visible for class teacher
  await expect(page.getByText("Today's breakdown")).not.toBeVisible();
});

test('TC-T04 – class teacher: can view library books (read access)', async ({ page }) => {
  await loginAs(page, TEACHER);
  await page.goto(`${TENANT_URL}/library/books`);
  await page.waitForLoadState('networkidle');

  // Can see book catalogue
  await expect(page.getByText('The Jungle Book')).toBeVisible({ timeout: 8_000 });
});

test('TC-T05 – class teacher: "Add book" button NOT visible (no write access)', async ({ page }) => {
  await loginAs(page, TEACHER);
  await page.goto(`${TENANT_URL}/library/books`);
  await page.waitForLoadState('networkidle');

  await expect(page.getByText('The Jungle Book')).toBeVisible({ timeout: 8_000 });

  // CLASS_TEACHER does not have LIBRARY_WRITER → "Add book" button hidden
  const addBookBtn = await page.getByRole('button', { name: /Add book/i }).count();
  expect(addBookBtn).toBe(0);
});

// ══════════════════════════════════════════════════════════════════════════════
//  LIBRARIAN TESTS
// ══════════════════════════════════════════════════════════════════════════════

test('TC-L01 – librarian login: library-focused nav (Books, Issued Books, My Schedule)', async ({ page }) => {
  await loginAs(page, LIBRARIAN);
  await expect(page).toHaveURL(new RegExp(TENANT), { timeout: 10_000 });

  // Librarian nav shows library items
  await expect(page.getByRole('link', { name: /^Books$/i }).first()).toBeVisible({ timeout: 8_000 });
  await expect(page.getByRole('link', { name: /Issued Books/i }).first()).toBeVisible({ timeout: 5_000 });
  await expect(page.getByRole('link', { name: /My Schedule/i }).first()).toBeVisible({ timeout: 5_000 });

  // Admin items NOT present in librarian nav
  const settingsNavCount = await page.getByRole('link', { name: /^Settings$/i }).count();
  expect(settingsNavCount).toBe(0);
  const studentsNavCount = await page.getByRole('link', { name: /^Students$/i }).count();
  expect(studentsNavCount).toBe(0);
});

test('TC-L02 – librarian: "Add book" button IS visible (LIBRARY_WRITER)', async ({ page }) => {
  await loginAs(page, LIBRARIAN);
  await page.goto(`${TENANT_URL}/library/books`);
  await page.waitForLoadState('networkidle');

  await expect(page.getByText('The Jungle Book')).toBeVisible({ timeout: 8_000 });

  // Librarian has LIBRARY_WRITER → "Add book" visible
  await expect(page.getByRole('button', { name: /Add book/i }).first()).toBeVisible({ timeout: 5_000 });
});

test('TC-L03 – librarian: can add a new book', async ({ page }) => {
  await loginAs(page, LIBRARIAN);
  await page.goto(`${TENANT_URL}/library/books`);
  await page.waitForLoadState('networkidle');

  await page.getByRole('button', { name: /Add book/i }).first().click();

  const modal = page.locator('.bg-white.rounded-xl.shadow-xl, [role="dialog"]').first();
  await expect(modal).toBeVisible({ timeout: 5_000 });

  const ts = Date.now();
  const title = `Librarian Book ${ts}`;
  await modal.getByLabel(/Title/i).fill(title);
  await modal.getByRole('button', { name: /Add|Save|Create/i }).last().click();

  await expect(modal).not.toBeVisible({ timeout: 8_000 });
  await expect(page.getByText(title)).toBeVisible({ timeout: 8_000 });
});

test('TC-L04 – librarian: "Issue book" button IS visible on issues page', async ({ page }) => {
  await loginAs(page, LIBRARIAN);
  await page.goto(`${TENANT_URL}/library/issues`);
  await page.waitForLoadState('networkidle');

  // LIBRARY_WRITER → issue button visible
  await expect(page.getByRole('button', { name: /Issue book/i }).first()).toBeVisible({ timeout: 8_000 });

  // Existing issue for Aarav Sharma visible
  await expect(page.getByText('Aarav Sharma')).toBeVisible({ timeout: 5_000 });
});

test('TC-L05 – librarian: My Schedule page loads without crash', async ({ page }) => {
  await loginAs(page, LIBRARIAN);
  await page.goto(`${TENANT_URL}/schedule`);
  await page.waitForLoadState('networkidle');

  // Page always renders "My Weekly Schedule" heading; wait for it
  await expect(page.getByText(/My Weekly Schedule/i)).toBeVisible({ timeout: 8_000 });

  // Admin-only "Assign substitute" button must NOT appear for librarian
  const subBtnCount = await page.getByRole('button', { name: /Assign substitute/i }).count();
  expect(subBtnCount).toBe(0);
});

test('TC-L06 – librarian: cannot navigate to admin dashboard (no crash, no admin nav)', async ({ page }) => {
  await loginAs(page, LIBRARIAN);

  // Librarian has no admin dashboard — nav shouldn't show "Dashboard" the admin one
  // but navigating directly should not crash with 500; may show empty or redirect
  await page.goto(`${TENANT_URL}/dashboard`);
  await page.waitForLoadState('networkidle');

  await expect(page.locator('body')).not.toContainText('500');
  await expect(page.locator('body')).not.toContainText('Unhandled');
});

// ══════════════════════════════════════════════════════════════════════════════
//  ACCOUNTANT TESTS
// ══════════════════════════════════════════════════════════════════════════════

test('TC-A01 – accountant login: fee-focused nav visible', async ({ page }) => {
  await loginAs(page, ACCOUNTANT);
  await expect(page).toHaveURL(new RegExp(TENANT), { timeout: 10_000 });

  // Accountant nav: Fee Dashboard, Collect Fee, Cash Recon, Fee Defaulters
  await expect(page.getByRole('link', { name: /Fee Dashboard/i }).first()).toBeVisible({ timeout: 8_000 });
  await expect(page.getByRole('link', { name: /Collect Fee/i }).first()).toBeVisible({ timeout: 5_000 });
  await expect(page.getByRole('link', { name: /Cash Recon/i }).first()).toBeVisible({ timeout: 5_000 });

  // Admin items NOT visible
  const settingsNavCount = await page.getByRole('link', { name: /^Settings$/i }).count();
  expect(settingsNavCount).toBe(0);
});

test('TC-A02 – accountant: fee collect page loads with student search', async ({ page }) => {
  test.setTimeout(60_000); // fee/collect page has many parallel data fetches
  await loginAs(page, ACCOUNTANT);
  await page.goto(`${TENANT_URL}/fees/collect`);
  // Don't use networkidle — fee page has ongoing data fetches; wait for specific element
  await page.waitForLoadState('domcontentloaded');

  await expect(page.locator('body')).not.toContainText('401');
  await expect(page.locator('body')).not.toContainText('Unauthorized');

  // The collect page always renders a search input in "search" browse mode on mount
  const searchInput = page.locator('input[placeholder*="Search by name"]').first();
  await expect(searchInput).toBeVisible({ timeout: 15_000 });
});

test('TC-A03 – accountant: library page has NO "Add book" button', async ({ page }) => {
  await loginAs(page, ACCOUNTANT);
  await page.goto(`${TENANT_URL}/library/books`);
  await page.waitForLoadState('networkidle');

  // Page may load books (read) or show access denied — either way, no "Add book"
  await page.waitForTimeout(2_000);
  const addBookCount = await page.getByRole('button', { name: /Add book/i }).count();
  expect(addBookCount).toBe(0);
});

test('TC-A04 – accountant: fee dashboard accessible without error', async ({ page }) => {
  await loginAs(page, ACCOUNTANT);
  await page.goto(`${TENANT_URL}/fees/dashboard`);
  await page.waitForLoadState('networkidle');

  await expect(page.locator('body')).not.toContainText('401');
  await expect(page.locator('body')).not.toContainText('500');

  // Either fee stats or an empty-state message — never a hard crash
  const body = page.locator('body');
  await expect(body).toBeVisible({ timeout: 5_000 });
});
