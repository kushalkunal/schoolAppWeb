/**
 * Live Role Sessions — opens 4 headed Chromium windows simultaneously,
 * each logged in as a different role so you can interact with the app.
 *
 * Run:
 *   cd web && npx playwright test e2e/role-sessions-live.spec.ts --headed --timeout=0
 *
 * Press Ctrl+C in the terminal (or close the browser windows) to stop.
 */

import { test, expect, type Browser, type Page } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

const BASE_URL   = 'http://localhost:3001';
const TENANT_ID  = '926c372c-139d-460d-83b1-1a80ef92db57';
const TENANT_URL = `${BASE_URL}/tenants/${TENANT_ID}`;

const SNAP_DIR = path.join(__dirname, '..', 'test-results', 'snapshots', 'role-sessions');
fs.mkdirSync(SNAP_DIR, { recursive: true });

const ACCOUNTS = [
  {
    role:      'PRINCIPAL',
    name:      'Rajesh Kumar',
    email:     'teacher@vms.school',
    password:  'Test@1234',
    landOn:    `${TENANT_URL}/dashboard`,
  },
  {
    role:      'CLASS TEACHER 1A',
    name:      'Ananya Singh',
    email:     'ananya.singh@vms.school',
    password:  'Teacher@123',
    landOn:    `${TENANT_URL}/dashboard`,
  },
  {
    role:      'CLASS TEACHER 1B',
    name:      'Rohan Mehta',
    email:     'rohan.mehta@vms.school',
    password:  'Teacher@123',
    landOn:    `${TENANT_URL}/dashboard`,
  },
  {
    role:      'ACCOUNTANT',
    name:      'Sunita Rao',
    email:     'accountant@vms.school',
    password:  'Teacher@123',
    landOn:    `${TENANT_URL}/fee`,
  },
  {
    role:      'LIBRARIAN',
    name:      'Vikram Das',
    email:     'librarian@vms.school',
    password:  'Teacher@123',
    landOn:    `${TENANT_URL}/library/books`,
  },
];

async function loginAs(page: Page, email: string, password: string) {
  await page.goto(`${BASE_URL}/login`);
  await page.waitForLoadState('domcontentloaded');

  // Switch to Email channel if the toggle is shown (default is PHONE)
  const emailTab = page.getByRole('button', { name: /^Email$/i });
  if (await emailTab.count() > 0) await emailTab.click();

  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
  await page.getByRole('button', { name: /^Sign in$/i }).click();

  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 60_000 });
}

// ── Single test — opens all 4 windows simultaneously ──────────────────────
test('All 5 roles — live interactive sessions', async ({ browser }: { browser: Browser }) => {
  // No timeout — windows stay open until you close them or press Ctrl+C
  test.setTimeout(0);

  const sessions: { page: Page; account: typeof ACCOUNTS[number] }[] = [];

  console.log('\n🚀  Opening sessions for all 5 roles...\n');

  for (const account of ACCOUNTS) {
    const ctx  = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    const page = await ctx.newPage();

    console.log(`🔐  Logging in: ${account.role} — ${account.name} (${account.email})`);
    await loginAs(page, account.email, account.password);
    console.log(`✅  Logged in successfully`);

    // Navigate to the role's default landing page
    await page.goto(account.landOn);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1500);

    // Screenshot for the record
    const snapFile = path.join(SNAP_DIR, `${account.role.toLowerCase().replace(/\s+/g, '-')}-live.png`);
    await page.screenshot({ path: snapFile, fullPage: false });
    console.log(`📸  Snapshot saved → ${path.basename(snapFile)}`);
    console.log(`📌  ${account.role} → ${account.landOn}\n`);

    sessions.push({ page, account });
  }

  console.log('╔══════════════════════════════════════════════════════════════╗');
  console.log('║  All 5 browser windows are OPEN and logged in!               ║');
  console.log('║                                                              ║');
  console.log('║  Window 1 → PRINCIPAL         teacher@vms.school            ║');
  console.log('║  Window 2 → CLASS TEACHER 1A  ananya.singh@vms.school       ║');
  console.log('║  Window 3 → CLASS TEACHER 1B  rohan.mehta@vms.school        ║');
  console.log('║  Window 4 → ACCOUNTANT         accountant@vms.school        ║');
  console.log('║  Window 5 → LIBRARIAN          librarian@vms.school         ║');
  console.log('║                                                              ║');
  console.log('║  Close any browser window OR press Ctrl+C to stop.          ║');
  console.log('╚══════════════════════════════════════════════════════════════╝\n');

  // Wait until ANY window is closed — then clean up and exit
  await Promise.race(
    sessions.map(({ page }) => page.waitForEvent('close', { timeout: 0 })),
  );

  console.log('\n🛑  A browser window was closed — ending session.');
});
