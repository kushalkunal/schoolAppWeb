/**
 * Open All Role Sessions
 *
 * Opens 4 visible Chromium windows, each logged in as a different role:
 *   Window 1 — PRINCIPAL      (Rajesh Kumar)
 *   Window 2 — CLASS_TEACHER  (Ananya Singh)
 *   Window 3 — LIBRARIAN      (Vikram Das)
 *   Window 4 — ACCOUNTANT     (Sunita Rao)
 *
 * Run:
 *   cd web && npx ts-node --project tsconfig.json scripts/open-all-sessions.ts
 *   OR
 *   cd web && npx playwright test scripts/open-all-sessions.ts --headed
 */

import { chromium } from '@playwright/test';
import * as path from 'path';

const BASE_URL   = 'http://localhost:3001';
const TENANT_ID  = '926c372c-139d-460d-83b1-1a80ef92db57';
const TENANT_URL = `${BASE_URL}/tenants/${TENANT_ID}`;

const ACCOUNTS = [
  {
    label:     'PRINCIPAL — Rajesh Kumar',
    email:     'teacher@vms.school',
    password:  'Test@1234',
    landOn:    `${TENANT_URL}/attendance`,
    color:     '#4F46E5',   // indigo
    x: 0,   y: 0,
    width: 1280, height: 800,
  },
  {
    label:     'CLASS TEACHER — Ananya Singh',
    email:     'ananya.singh@vms.school',
    password:  'Teacher@123',
    landOn:    `${TENANT_URL}/attendance`,
    color:     '#0891B2',   // cyan
    x: 0,   y: 0,
    width: 1280, height: 800,
  },
  {
    label:     'LIBRARIAN — Vikram Das',
    email:     'librarian@vms.school',
    password:  'Teacher@123',
    landOn:    `${TENANT_URL}/library/books`,
    color:     '#059669',   // emerald
    x: 0,   y: 0,
    width: 1280, height: 800,
  },
  {
    label:     'ACCOUNTANT — Sunita Rao',
    email:     'accountant@vms.school',
    password:  'Teacher@123',
    landOn:    `${TENANT_URL}/fee`,
    color:     '#D97706',   // amber
    x: 0,   y: 0,
    width: 1280, height: 800,
  },
] as const;

async function loginAs(page: any, email: string, password: string) {
  await page.goto(`${BASE_URL}/login`);
  await page.waitForLoadState('networkidle');

  // Switch to Email channel if needed
  const emailTab = page.getByRole('button', { name: /^Email$/i });
  if (await emailTab.count() > 0) await emailTab.click();

  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
  await page.getByRole('button', { name: /^Sign in$/i }).click();

  // Wait for redirect away from login
  await page.waitForURL((url: URL) => !url.pathname.startsWith('/login'), { timeout: 25_000 });
}

(async () => {
  const browser = await chromium.launch({
    headless: false,
    args: ['--start-maximized'],
  });

  console.log('\n🚀  Opening sessions for all 4 roles...\n');

  const contexts: any[] = [];

  for (const account of ACCOUNTS) {
    const ctx = await browser.newContext({
      viewport: { width: account.width, height: account.height },
    });

    const page = await ctx.newPage();

    console.log(`🔐  Logging in: ${account.label}`);
    try {
      await loginAs(page, account.email, account.password);
      console.log(`✅  Logged in — navigating to dashboard`);

      await page.goto(account.landOn);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(1500);

      console.log(`📌  ${account.label} → ${account.landOn}\n`);
    } catch (err) {
      console.error(`❌  Failed to log in ${account.label}:`, err);
    }

    contexts.push({ ctx, page, account });
  }

  console.log('═══════════════════════════════════════════════════════');
  console.log('  All 4 sessions are OPEN and logged in!');
  console.log('');
  console.log('  Window 1 (Indigo)  → PRINCIPAL      teacher@vms.school');
  console.log('  Window 2 (Cyan)    → CLASS TEACHER  ananya.singh@vms.school');
  console.log('  Window 3 (Emerald) → LIBRARIAN      librarian@vms.school');
  console.log('  Window 4 (Amber)   → ACCOUNTANT     accountant@vms.school');
  console.log('');
  console.log('  Press Ctrl+C to close all windows.');
  console.log('═══════════════════════════════════════════════════════\n');

  // Keep alive until Ctrl+C
  await new Promise((resolve) => {
    process.on('SIGINT', async () => {
      console.log('\n🛑  Closing all sessions...');
      await browser.close();
      resolve(undefined);
    });
    process.on('SIGTERM', async () => {
      await browser.close();
      resolve(undefined);
    });
  });
})();
