/**
 * E2E: Frontend per-role route-guard enforcement (audit #4).
 *
 * Validates the RequireRouteAccess guard + ROLE_AREAS source of truth: each role can only reach
 * its permitted screens, forbidden routes redirect to the dashboard, and the nav only shows
 * permitted links. Backend-free — sessions are seeded and the API is mocked, so these run
 * deterministically without a live backend (see _session.ts).
 */
import { test, expect } from '@playwright/test';
import { loginAs, mockApi, TENANT } from './_session';

const base = `/tenants/${TENANT}`;
const dashboardUrl = new RegExp(`${base}/dashboard`);

// [role, allowed routes, forbidden routes]
const MATRIX: Array<[string, string[], string[]]> = [
  ['CLASS_TEACHER', [`${base}/dashboard`, `${base}/attendance`, `${base}/academics/exams`, `${base}/homework`],
                    [`${base}/fees/collect`, `${base}/library/books`, `${base}/settings/school`, `${base}/hr/payroll`]],
  ['ACCOUNTANT',    [`${base}/fees/dashboard`, `${base}/expenses`],
                    [`${base}/attendance`, `${base}/academics/exams`, `${base}/library/books`, `${base}/settings/school`]],
  ['LIBRARIAN',     [`${base}/library/books`, `${base}/dashboard`],
                    [`${base}/fees/dashboard`, `${base}/attendance`, `${base}/settings/school`]],
  ['VIEWER',        [`${base}/dashboard`, `${base}/students`],
                    [`${base}/fees/collect`, `${base}/settings/school`, `${base}/hr/payroll`]],
  ['ADMIN',         [`${base}/fees/collect`, `${base}/settings/school`, `${base}/attendance`, `${base}/library/books`], []],
];

for (const [role, allowed, forbidden] of MATRIX) {
  test.describe(`route access — ${role}`, () => {
    for (const route of allowed) {
      test(`can open ${route}`, async ({ page }) => {
        await loginAs(page, role);
        await page.goto(route);
        await expect(page).toHaveURL(new RegExp(route.replace(/[/]/g, '\\/')));
      });
    }
    for (const route of forbidden) {
      test(`is redirected away from ${route}`, async ({ page }) => {
        await loginAs(page, role);
        await page.goto(route);
        await expect(page).toHaveURL(dashboardUrl);
      });
    }
  });
}

test.describe('navigation visibility', () => {
  test('accountant sees fee links but not student/library/settings links', async ({ page }) => {
    await loginAs(page, 'ACCOUNTANT');
    await page.goto(`${base}/fees/dashboard`);
    const sidebar = page.locator('aside');             // desktop nav; avoids mobile-nav duplicates
    await expect(sidebar.getByRole('link', { name: /Fee Dashboard/i })).toBeVisible();
    await expect(sidebar.getByRole('link', { name: /^Students$/ })).toHaveCount(0);
    await expect(sidebar.getByRole('link', { name: /^Settings$/ })).toHaveCount(0);
  });

  test('class teacher sees teacher nav, not fees/settings', async ({ page }) => {
    await loginAs(page, 'CLASS_TEACHER');
    await page.goto(`${base}/dashboard`);
    const sidebar = page.locator('aside');
    await expect(sidebar.getByRole('link', { name: /My Schedule/i })).toBeVisible();
    await expect(sidebar.getByRole('link', { name: /Collect Fee/i })).toHaveCount(0);
    await expect(sidebar.getByRole('link', { name: /^Settings$/ })).toHaveCount(0);
  });
});

test.describe('authentication', () => {
  test('anonymous visitor is redirected to login', async ({ page }) => {
    await mockApi(page); // no session seeded
    await page.goto(`${base}/dashboard`);
    await expect(page).toHaveURL(/\/login/);
  });

  test('a tenant mismatch is redirected to login', async ({ page }) => {
    await loginAs(page, 'ADMIN', '22222222-2222-2222-2222-222222222222'); // token tenant != URL tenant
    await page.goto(`${base}/dashboard`);
    await expect(page).toHaveURL(/\/login/);
  });
});
