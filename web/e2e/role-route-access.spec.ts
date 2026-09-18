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

// Some roles don't have a generic dashboard — the dashboard page itself redirects them to their
// home screen (see dashboard/page.tsx): ACCOUNTANT → fees, LIBRARIAN → library, RECEPTIONIST →
// visitors. A forbidden route is bounced to /dashboard by the route guard, which then re-redirects
// these roles onward to that home. So assertions are made against each role's effective home.
const HOME: Record<string, string> = {
  CLASS_TEACHER: `${base}/dashboard`,
  ACCOUNTANT:    `${base}/fees/dashboard`,
  LIBRARIAN:     `${base}/library/books`,
  VIEWER:        `${base}/dashboard`,
  ADMIN:         `${base}/dashboard`,
};

// [role, allowed routes, forbidden routes]. Allowed routes are ones the role lands on directly
// (don't list /dashboard for a role that gets redirected off it — assert its home instead).
const MATRIX: Array<[string, string[], string[]]> = [
  ['CLASS_TEACHER', [`${base}/dashboard`, `${base}/attendance`, `${base}/academics/exams`, `${base}/homework`],
                    [`${base}/fees/collect`, `${base}/library/books`, `${base}/settings/school`, `${base}/hr/payroll`]],
  ['ACCOUNTANT',    [`${base}/fees/dashboard`, `${base}/expenses`],
                    [`${base}/attendance`, `${base}/academics/exams`, `${base}/library/books`, `${base}/settings/school`]],
  ['LIBRARIAN',     [`${base}/library/books`],
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
        // Bounced to /dashboard by the guard, then on to the role's home for redirecting roles.
        await expect(page).toHaveURL(new RegExp(HOME[role]!.replace(/[/]/g, '\\/')));
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

  // The URL's tenant segment is now a cosmetic, human-readable slug — it is NEVER trusted for
  // access control. The authoritative tenant is the JWT's `tenantId` claim, enforced on every API
  // call (the client rewrites the slug to the real UUID) and by row-level security on the backend.
  // So an authenticated user is NOT bounced to /login just because the URL segment differs from
  // their token's tenant; they simply only ever see their own school's data. (This is the
  // client-side half of the fix/a1-tenant-isolation-idor change — isolation moved server-side.)
  test('an authenticated user is not redirected by a cosmetic tenant slug in the URL', async ({ page }) => {
    await loginAs(page, 'ADMIN', '22222222-2222-2222-2222-222222222222'); // token tenant != URL segment
    await page.goto(`${base}/dashboard`);
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page).toHaveURL(/\/tenants\/[^/]+\/dashboard/);
  });
});
