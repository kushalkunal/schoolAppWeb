/**
 * E2E: core user workflows (API-mocked, backend-free).
 *
 * Exercises real multi-step flows in the browser — the login form + token bootstrap, and
 * click-through navigation — without a live backend (see _session.ts for the mocking strategy).
 */
import { test, expect } from '@playwright/test';
import { fakeJwt, loginAs, mockApi, TENANT } from './_session';

test.describe('login workflow', () => {
  test('password sign-in stores a session and lands in the tenant area', async ({ page }) => {
    await mockApi(page);
    // The real backend wire shape: envelope -> data -> { accessToken, ... }.
    await page.route('**/api/v1/auth/password/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            accessToken: fakeJwt('ADMIN', TENANT),
            refreshToken: 'fake-refresh',
            expiresInSeconds: 3600,
            mustResetPassword: false,
          },
        }),
      });
    });

    await page.goto('/login');
    await page.getByPlaceholder('+91 98765 43210').fill('9876543210');
    await page.getByPlaceholder('Password').fill('Secret@123');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();

    // Lands in the tenant area. The URL segment is a cosmetic slug derived from branding, not the
    // UUID — the JWT's tenantId is authoritative.
    await expect(page).toHaveURL(/\/tenants\/[^/]+\/dashboard/);
  });
});

test.describe('navigation workflow', () => {
  test('class teacher clicks through the sidebar from dashboard to schedule', async ({ page }) => {
    await loginAs(page, 'CLASS_TEACHER');
    await page.goto(`/tenants/${TENANT}/dashboard`);

    const sidebar = page.locator('aside');
    await sidebar.getByRole('link', { name: /My Schedule/i }).click();

    await expect(page).toHaveURL(new RegExp(`/tenants/${TENANT}/schedule`));
  });
});
