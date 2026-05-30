import type { Page } from '@playwright/test';

/** A tenant UUID used consistently across the frontend route-guard specs. */
export const TENANT = '11111111-1111-1111-1111-111111111111';

function base64url(obj: unknown): string {
  return Buffer.from(JSON.stringify(obj))
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

/**
 * Crafts a JWT the frontend will accept. The app only base64-decodes the payload to read claims
 * (the backend already verified the signature on issue), so an unsigned token with the right
 * claims is sufficient for frontend behaviour tests.
 */
export function fakeJwt(role: string, tenantId = TENANT): string {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url({ alg: 'HS256', typ: 'JWT' });
  const payload = base64url({
    sub: '00000000-0000-0000-0000-0000000000aa',
    tenantId,
    role,
    name: `${role} User`,
    iat: now,
    exp: now + 3600,
  });
  return `${header}.${payload}.sig`;
}

/**
 * Mocks every backend call so the SPA renders without a live API and the axios interceptor never
 * 401-redirects to /login. Returns benign success envelopes; branding gets a real-ish object so
 * the layout/branding provider renders.
 */
export async function mockApi(page: Page): Promise<void> {
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url();
    let data: unknown = [];
    if (url.includes('/branding')) {
      data = { schoolName: 'Test School', affiliation: 'CBSE', primaryColor: '#4f46e5', logoUrl: null };
    } else if (url.includes('/onboarding-status')) {
      data = { complete: true };
    } else if (url.includes('/dashboard')) {
      data = {};
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data }),
    });
  });
}

/** Seeds an authenticated session for {@code role} and mocks the API. Call before page.goto. */
export async function loginAs(page: Page, role: string, tenantId = TENANT): Promise<void> {
  await mockApi(page);
  const token = fakeJwt(role, tenantId);
  const expiresAt = Date.now() + 3600_000;
  await page.addInitScript(
    ([t, exp]) => {
      localStorage.setItem('sms.accessToken', t as string);
      localStorage.setItem('sms.refreshToken', 'fake-refresh');
      localStorage.setItem('sms.expiresAt', String(exp));
    },
    [token, expiresAt] as const,
  );
}
