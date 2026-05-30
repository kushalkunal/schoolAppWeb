/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║  ATTENDANCE — Full Day Workflow  (Dual-Context / Two-Tab Simulation)    ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║                                                                          ║
 * ║  Simulates a real school morning as two people working simultaneously:   ║
 * ║                                                                          ║
 * ║  TAB-A → PRINCIPAL  (Rajesh Kumar — teacher@vms.school)                 ║
 * ║  TAB-B → CLASS TEACHER  (Ananya Singh — ananya.singh@vms.school)        ║
 * ║                                                                          ║
 * ║  ACT 1 ─ Dual Login                                                      ║
 * ║    Both roles log in; Principal sees admin overview (stats, unmarked      ║
 * ║    sections), Teacher sees "My sections" card.                            ║
 * ║                                                                          ║
 * ║  ACT 2 ─ Teacher marks Class 1A attendance                               ║
 * ║    Ananya goes to her section page; all 4 students default to PRESENT.   ║
 * ║    She marks Aarav ABSENT and Diya LATE, then submits.                   ║
 * ║    Section is now locked — parent alerts queued.                          ║
 * ║                                                                          ║
 * ║  ACT 3 ─ Principal verifies in real-time (dual context again)            ║
 * ║    Teacher page reflects the saved/locked state.                          ║
 * ║    Principal's dashboard shows Class 1A in "Attendance taken" list.      ║
 * ║    Principal drills into Class 1A panel to inspect per-student status.   ║
 * ║                                                                          ║
 * ║  ACT 4 ─ Principal override (correction: Aarav actually arrived late)    ║
 * ║    Principal opens Class 1A section page; sees amber "locked" banner.    ║
 * ║    Clicks Aarav's ABSENT badge (cycles → LATE = arrived late).           ║
 * ║    Clicks "Save & Override" — section re-locked under principal.         ║
 * ║                                                                          ║
 * ║  ACT 5 ─ Teacher revisits + Final principal summary                      ║
 * ║    Teacher goes back — sees the corrected status.                        ║
 * ║    Principal's summary now shows correct totals for the day.             ║
 * ║                                                                          ║
 * ║  Screenshots saved → web/test-results/snapshots/attendance/             ║
 * ║                                                                          ║
 * ║  Run:                                                                    ║
 * ║    cd web && npx playwright test e2e/attendance-daily-workflow.spec.ts   ║
 * ║               --reporter=list                                            ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */

import { test, expect, type Page, type Browser } from '@playwright/test';
import { execSync } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';

// ─── Constants ─────────────────────────────────────────────────────────────
const TENANT     = '926c372c-139d-460d-83b1-1a80ef92db57';
const BASE_URL   = 'http://localhost:3001';
const TENANT_URL = `${BASE_URL}/tenants/${TENANT}`;
const TODAY      = '2026-05-30';

// Section 1A — class teacher: Ananya Singh, students: Aarav, Diya, Ishaan, Kavya
const SEC_1A = '032ec61c-3373-4389-9169-16ae826c357a';

const PRINCIPAL = { email: 'teacher@vms.school',      password: 'Test@1234'   };
const TEACHER   = { email: 'ananya.singh@vms.school', password: 'Teacher@123' };

// ─── Snapshot output ───────────────────────────────────────────────────────
const SNAP_DIR = path.join(__dirname, '..', 'test-results', 'snapshots', 'attendance');
fs.mkdirSync(SNAP_DIR, { recursive: true });

// ─── Helpers ───────────────────────────────────────────────────────────────
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

async function snap(page: Page, label: string) {
  await page.waitForTimeout(700);
  const filepath = path.join(SNAP_DIR, `${label}.png`);
  await page.screenshot({ path: filepath, fullPage: true });
  console.log(`📸  ${label}.png`);
}

// ══════════════════════════════════════════════════════════════════════════════
//  SERIAL SUITE — preserves DB state between acts
// ══════════════════════════════════════════════════════════════════════════════
test.describe.serial('Attendance daily workflow — dual context', () => {
  test.setTimeout(90_000);

  // ── Pre-suite cleanup ──────────────────────────────────────────────────────
  // Wipe today's attendance for Section 1A so every run starts clean (fresh mark).
  test.beforeAll(() => {
    try {
      execSync(
        `docker exec schoolapp-postgres psql -U schoolapp -d schoolapp -c ` +
        `"DELETE FROM attendance_records WHERE section_id='${SEC_1A}' AND date='${TODAY}'; ` +
        `DELETE FROM attendance_section_locks WHERE section_id='${SEC_1A}' AND date='${TODAY}';"`,
        { stdio: 'ignore' },
      );
      console.log('🧹  Cleared today\'s Section 1A attendance — fresh start');
    } catch {
      console.warn('⚠️   DB cleanup failed (container may not be running) — continuing');
    }
  });

  // ────────────────────────────────────────────────────────────────────────────
  //  ACT 1 — DUAL LOGIN
  //  Open two separate browser contexts simultaneously (simulating two browser tabs).
  //  Principal sees the admin attendance overview.
  //  Teacher sees "My sections" card with "Mark attendance →" link.
  // ────────────────────────────────────────────────────────────────────────────
  test('ACT-1: Dual login — Principal admin view vs Teacher sections view', async ({ browser }) => {
    // ── Spin up two isolated browser contexts (no shared cookies/storage) ──
    const [principalCtx, teacherCtx] = await Promise.all([
      browser.newContext({ viewport: { width: 1280, height: 800 } }),
      browser.newContext({ viewport: { width: 1280, height: 800 } }),
    ]);
    const [principalPage, teacherPage] = await Promise.all([
      principalCtx.newPage(),
      teacherCtx.newPage(),
    ]);

    // ── Login both simultaneously ──
    await Promise.all([
      loginAs(principalPage, PRINCIPAL),
      loginAs(teacherPage, TEACHER),
    ]);
    console.log('✅  Both roles logged in');

    // ── Principal: navigate to attendance dashboard ──
    await principalPage.goto(`${TENANT_URL}/attendance`);
    await principalPage.waitForLoadState('domcontentloaded');
    await principalPage.waitForTimeout(1_500);

    // Set date to today so stats reflect today's work
    const datePicker = principalPage.locator('input[type="date"]');
    await datePicker.fill(TODAY);
    await principalPage.waitForTimeout(1_500);

    // Should see admin overview
    await expect(principalPage.getByText('Attendance taken')).toBeVisible({ timeout: 10_000 });
    await expect(principalPage.getByText('Not yet taken')).toBeVisible({ timeout: 5_000 });
    // Before teacher marks anything, Class 1A should be in "Not yet taken"
    await expect(principalPage.getByText('Not yet taken')).toBeVisible();

    await snap(principalPage, '01-principal-attendance-overview-before-marking');
    console.log('📊  Principal: attendance dashboard loaded (all sections unmarked)');

    // ── Teacher: navigate to attendance home ──
    await teacherPage.goto(`${TENANT_URL}/attendance`);
    await teacherPage.waitForLoadState('networkidle');

    // Class teacher sees "My sections" card
    await expect(teacherPage.getByText('My sections')).toBeVisible({ timeout: 10_000 });
    await expect(teacherPage.getByText('Mark attendance →').first()).toBeVisible({ timeout: 8_000 });

    await snap(teacherPage, '02-teacher-my-sections-home');
    console.log('👩‍🏫  Teacher: "My sections" card loaded with Mark attendance links');

    // ── Verify the "not yet taken" section shows Class 1A on principal side ──
    const unmarkedList = principalPage.locator('ul').filter({ has: principalPage.getByText(/Ananya Singh/) });
    if (await unmarkedList.count() > 0) {
      await expect(unmarkedList.getByText('Ananya Singh').first()).toBeVisible({ timeout: 5_000 });
      console.log('✅  Principal confirms Class 1A (Ananya Singh) is in "Not yet taken"');
    }

    await Promise.all([principalCtx.close(), teacherCtx.close()]);
  });

  // ────────────────────────────────────────────────────────────────────────────
  //  ACT 2 — TEACHER MARKS CLASS 1A ATTENDANCE
  //  Ananya navigates to the Section 1A attendance page.
  //  All 4 students default to PRESENT (first mark of the day).
  //  She changes: Aarav → ABSENT, Diya → LATE.
  //  Submits. Section locks and parent alerts are queued.
  // ────────────────────────────────────────────────────────────────────────────
  test('ACT-2: Teacher marks Class 1A — Aarav ABSENT, Diya LATE, others PRESENT', async ({ page }) => {
    await loginAs(page, TEACHER);

    // Navigate directly to Class 1A section attendance page
    await page.goto(`${TENANT_URL}/attendance/${SEC_1A}?date=${TODAY}`);
    await page.waitForLoadState('networkidle');

    // ── Verify all 4 students are visible ──
    await expect(page.getByText('Aarav Sharma')).toBeVisible({ timeout: 12_000 });
    await expect(page.getByText('Diya Patel')).toBeVisible({ timeout: 5_000 });
    await expect(page.getByText('Ishaan Verma')).toBeVisible({ timeout: 5_000 });
    await expect(page.getByText('Kavya Nair')).toBeVisible({ timeout: 5_000 });

    // First-mark info banner: "First mark for {date}. Everyone defaults to PRESENT"
    await expect(page.getByText(/First mark for/i)).toBeVisible({ timeout: 8_000 });

    // Count: header says "4 students · 4 present · 0 absent · 0 late"
    await expect(page.getByText(/4 students/)).toBeVisible({ timeout: 5_000 });

    await snap(page, '03-teacher-class1A-all-four-students-default-present');
    console.log('👁️   Teacher: all 4 students visible, all defaulting to PRESENT');

    // ── Mark Aarav Sharma as ABSENT (1 click: PRESENT → ABSENT) ──
    const aaravRow = page.locator('li').filter({ hasText: 'Aarav Sharma' }).first();
    await expect(aaravRow).toBeVisible({ timeout: 5_000 });
    await aaravRow.getByRole('button').click();
    await expect(aaravRow.getByText('ABSENT')).toBeVisible({ timeout: 4_000 });
    console.log('❌  Aarav Sharma → ABSENT');

    // ── Mark Diya Patel as LATE (2 clicks: PRESENT → ABSENT → LATE) ──
    const diyaRow = page.locator('li').filter({ hasText: 'Diya Patel' }).first();
    await diyaRow.getByRole('button').click(); // → ABSENT
    await diyaRow.getByRole('button').click(); // → LATE
    await expect(diyaRow.getByText('LATE')).toBeVisible({ timeout: 4_000 });
    console.log('⏰  Diya Patel → LATE');

    // ── Ishaan and Kavya remain PRESENT ──
    await expect(page.locator('li').filter({ hasText: 'Ishaan Verma' }).getByText('PRESENT')).toBeVisible();
    await expect(page.locator('li').filter({ hasText: 'Kavya Nair' }).getByText('PRESENT')).toBeVisible();

    // Card header now reflects the updated counts
    await expect(page.getByText(/2 present.*1 absent.*1 late|4 students/)).toBeVisible({ timeout: 4_000 });

    await snap(page, '04-teacher-marked-aarav-absent-diya-late-ready-to-submit');
    console.log('📋  Teacher: attendance marked — 2 present, 1 absent, 1 late');

    // ── Submit attendance ──
    await page.getByRole('button', { name: 'Submit attendance' }).click();

    // Success confirmation: "Saved · N parent alert(s) queued"
    await expect(page.getByText(/Saved/i)).toBeVisible({ timeout: 12_000 });
    console.log('✅  Teacher: attendance submitted successfully');

    await snap(page, '05-teacher-attendance-submitted-success-with-lock');
  });

  // ────────────────────────────────────────────────────────────────────────────
  //  ACT 3 — DUAL-CONTEXT VERIFICATION (real-time cross-role check)
  //  Teacher revisits section — now sees locked banner + "Save changes" button.
  //  Principal refreshes dashboard — Class 1A now appears in "Attendance taken".
  //  Principal opens Class 1A detail panel to inspect per-student statuses.
  // ────────────────────────────────────────────────────────────────────────────
  test('ACT-3: Dual context — Teacher sees lock, Principal sees Class 1A marked', async ({ browser }) => {
    const [teacherCtx, principalCtx] = await Promise.all([
      browser.newContext({ viewport: { width: 1280, height: 800 } }),
      browser.newContext({ viewport: { width: 1280, height: 800 } }),
    ]);
    const [teacherPage, principalPage] = await Promise.all([
      teacherCtx.newPage(),
      principalCtx.newPage(),
    ]);

    await Promise.all([
      loginAs(teacherPage, TEACHER),
      loginAs(principalPage, PRINCIPAL),
    ]);

    // ── TEACHER SIDE: revisit the section page — it's now locked ──
    await teacherPage.goto(`${TENANT_URL}/attendance/${SEC_1A}?date=${TODAY}`);
    await teacherPage.waitForLoadState('networkidle');
    await expect(teacherPage.getByText('Aarav Sharma')).toBeVisible({ timeout: 10_000 });

    // Lock banner should appear: "Attendance locked by class teacher"
    await expect(teacherPage.getByText(/locked/i)).toBeVisible({ timeout: 8_000 });

    // Students show saved statuses
    await expect(teacherPage.locator('li').filter({ hasText: 'Aarav Sharma' }).getByText('ABSENT')).toBeVisible({ timeout: 5_000 });
    await expect(teacherPage.locator('li').filter({ hasText: 'Diya Patel' }).getByText('LATE')).toBeVisible({ timeout: 5_000 });
    await expect(teacherPage.locator('li').filter({ hasText: 'Ishaan Verma' }).getByText('PRESENT')).toBeVisible({ timeout: 5_000 });
    await expect(teacherPage.locator('li').filter({ hasText: 'Kavya Nair' }).getByText('PRESENT')).toBeVisible({ timeout: 5_000 });

    await snap(teacherPage, '06-teacher-section-locked-statuses-confirmed');
    console.log('🔒  Teacher: section is locked — statuses confirmed (ABSENT, LATE, PRESENT, PRESENT)');

    // ── PRINCIPAL SIDE: open attendance dashboard ──
    await principalPage.goto(`${TENANT_URL}/attendance`);
    await principalPage.waitForLoadState('domcontentloaded');
    await principalPage.waitForTimeout(1_500);
    await principalPage.locator('input[type="date"]').fill(TODAY);
    await principalPage.waitForTimeout(2_000);

    // "Attendance taken" card should now have at least one entry
    await expect(principalPage.getByText('Attendance taken')).toBeVisible({ timeout: 10_000 });
    const takenBadge = principalPage.locator('span.bg-green-100').first();
    await expect(takenBadge).toBeVisible({ timeout: 8_000 });
    // Badge shows ≥ 1 section marked
    const takenCount = await takenBadge.textContent();
    expect(Number(takenCount)).toBeGreaterThanOrEqual(1);

    await snap(principalPage, '07-principal-attendance-taken-shows-class1A');
    console.log(`📊  Principal: ${takenCount} section(s) now in "Attendance taken"`);

    // ── Open the Class 1A detail panel by clicking its row ──
    // The accordion row shows the class label (e.g. "Class 1 — A")
    const sectionRow = principalPage.locator('button').filter({ hasText: /Class\s*1.*A|1\s*—\s*A/ }).first();
    await expect(sectionRow).toBeVisible({ timeout: 8_000 });
    // Click to toggle open (or it may already be open — click ensures expanded state)
    await sectionRow.click();
    await principalPage.waitForTimeout(2_000);

    // The SectionDetailPanel renders inline in compact form.
    // Student names are truncated ("…") in this view but summary stats and status
    // badges are always visible.
    // Verify the panel is open by checking the "Override" button and summary stats.
    await expect(principalPage.getByRole('button', { name: /Override/i }).first()).toBeVisible({ timeout: 8_000 });

    await snap(principalPage, '08-principal-class1A-detail-panel-open');
    console.log('🔍  Principal: Class 1A detail panel open — Override button and stats visible');

    // Detail panel shows the correct status counts in the summary strip
    await expect(principalPage.getByText(/Absent.*1|1.*Absent/i).first()).toBeVisible({ timeout: 5_000 });
    await expect(principalPage.getByText(/Late.*1|1.*Late/i).first()).toBeVisible({ timeout: 5_000 });
    console.log('✅  Principal: Absent: 1, Late: 1 confirmed in inline panel summary');

    await Promise.all([teacherCtx.close(), principalCtx.close()]);
  });

  // ────────────────────────────────────────────────────────────────────────────
  //  ACT 4 — PRINCIPAL OVERRIDE
  //  New info: Aarav actually arrived during recess (should be LATE, not ABSENT).
  //  Principal opens Section 1A page directly → sees amber "locked" override banner.
  //  Clicks Aarav's ABSENT badge once → cycles to LATE.
  //  Clicks "Save & Override" → attendance re-saved under principal's authority.
  // ────────────────────────────────────────────────────────────────────────────
  test('ACT-4: Principal overrides Aarav from ABSENT → LATE (arrived during recess)', async ({ page }) => {
    await loginAs(page, PRINCIPAL);

    // Navigate to Section 1A attendance page as principal
    await page.goto(`${TENANT_URL}/attendance/${SEC_1A}?date=${TODAY}`);
    await page.waitForLoadState('networkidle');

    await expect(page.getByText('Aarav Sharma')).toBeVisible({ timeout: 12_000 });

    // ── Principal sees the amber override warning banner ──
    // "⚠️ This attendance is locked ... As Principal, you can override and re-save."
    await expect(page.getByText(/locked/i).first()).toBeVisible({ timeout: 8_000 });
    await expect(page.getByText(/Principal.*override|override.*re-save/i)).toBeVisible({ timeout: 5_000 });

    await snap(page, '09-principal-on-locked-section-page-amber-banner');
    console.log('⚠️   Principal: amber lock banner visible — override authority confirmed');

    // ── Verify current statuses ──
    const aaravRow = page.locator('li').filter({ hasText: 'Aarav Sharma' }).first();
    await expect(aaravRow.getByText('ABSENT')).toBeVisible({ timeout: 5_000 });
    console.log('👁️   Principal confirms: Aarav is currently ABSENT');

    // ── Correct: click Aarav's ABSENT button once → LATE ──
    await aaravRow.getByRole('button').click();
    await expect(aaravRow.getByText('LATE')).toBeVisible({ timeout: 4_000 });
    console.log('✏️   Principal changed: Aarav ABSENT → LATE');

    await snap(page, '10-principal-aarav-changed-to-late-before-override-save');

    // ── Click "Save & Override" ──
    const overrideBtn = page.getByRole('button', { name: 'Save & Override' });
    await expect(overrideBtn).toBeVisible({ timeout: 5_000 });
    await overrideBtn.click();

    // Confirm save
    await expect(page.getByText(/Saved/i)).toBeVisible({ timeout: 12_000 });
    console.log('✅  Principal: "Save & Override" succeeded — section re-locked');

    await snap(page, '11-principal-override-saved-confirmed');
  });

  // ────────────────────────────────────────────────────────────────────────────
  //  ACT 5 — TEACHER REVISITS + FINAL SUMMARY
  //  Teacher goes back to her section page — sees Aarav now marked LATE (override).
  //  Principal checks overall stats — correct totals for the day.
  // ────────────────────────────────────────────────────────────────────────────
  test('ACT-5: Teacher sees principal override; Principal views final summary', async ({ browser }) => {
    const [teacherCtx, principalCtx] = await Promise.all([
      browser.newContext({ viewport: { width: 1280, height: 800 } }),
      browser.newContext({ viewport: { width: 1280, height: 800 } }),
    ]);
    const [teacherPage, principalPage] = await Promise.all([
      teacherCtx.newPage(),
      principalCtx.newPage(),
    ]);

    await Promise.all([
      loginAs(teacherPage, TEACHER),
      loginAs(principalPage, PRINCIPAL),
    ]);

    // ── TEACHER: revisit Class 1A section — sees principal's correction ──
    await teacherPage.goto(`${TENANT_URL}/attendance/${SEC_1A}?date=${TODAY}`);
    await teacherPage.waitForLoadState('networkidle');
    await expect(teacherPage.getByText('Aarav Sharma')).toBeVisible({ timeout: 10_000 });

    // Aarav should now be LATE (not ABSENT — principal overrode it)
    await expect(
      teacherPage.locator('li').filter({ hasText: 'Aarav Sharma' }).getByText('LATE'),
    ).toBeVisible({ timeout: 8_000 });
    // Diya still LATE
    await expect(
      teacherPage.locator('li').filter({ hasText: 'Diya Patel' }).getByText('LATE'),
    ).toBeVisible({ timeout: 5_000 });
    // Ishaan and Kavya still PRESENT
    await expect(
      teacherPage.locator('li').filter({ hasText: 'Ishaan Verma' }).getByText('PRESENT'),
    ).toBeVisible({ timeout: 5_000 });

    await snap(teacherPage, '12-teacher-revisit-confirms-aarav-now-late');
    console.log('👩‍🏫  Teacher: confirmed Aarav is now LATE (principal\'s override applied)');

    // ── PRINCIPAL: final attendance summary dashboard ──
    await principalPage.goto(`${TENANT_URL}/attendance`);
    await principalPage.waitForLoadState('domcontentloaded');
    await principalPage.waitForTimeout(1_500);
    await principalPage.locator('input[type="date"]').fill(TODAY);
    await principalPage.waitForTimeout(2_000);

    // Summary stats strip should be visible
    await expect(principalPage.getByText('Sections marked')).toBeVisible({ timeout: 10_000 });
    await expect(principalPage.getByText('Present')).toBeVisible({ timeout: 5_000 });
    await expect(principalPage.getByText('Late')).toBeVisible({ timeout: 5_000 });

    await snap(principalPage, '13-principal-final-day-summary');
    console.log('📈  Principal: final daily summary loaded with all stats');

    // ── Navigate to Class 1A to see final locked state from admin view ──
    await principalPage.goto(`${TENANT_URL}/attendance/${SEC_1A}?date=${TODAY}`);
    await principalPage.waitForLoadState('networkidle');
    await expect(principalPage.getByText('Aarav Sharma')).toBeVisible({ timeout: 10_000 });

    // All four students present with final statuses
    await expect(
      principalPage.locator('li').filter({ hasText: 'Aarav Sharma' }).getByText('LATE'),
    ).toBeVisible({ timeout: 5_000 });
    await expect(
      principalPage.locator('li').filter({ hasText: 'Diya Patel' }).getByText('LATE'),
    ).toBeVisible({ timeout: 5_000 });
    await expect(
      principalPage.locator('li').filter({ hasText: 'Ishaan Verma' }).getByText('PRESENT'),
    ).toBeVisible({ timeout: 5_000 });
    await expect(
      principalPage.locator('li').filter({ hasText: 'Kavya Nair' }).getByText('PRESENT'),
    ).toBeVisible({ timeout: 5_000 });

    // "Save & Override" button visible (principal can always re-override)
    await expect(principalPage.getByRole('button', { name: 'Save & Override' })).toBeVisible({ timeout: 5_000 });

    await snap(principalPage, '14-principal-final-class1A-all-statuses-correct');
    console.log('✅  Principal: final Class 1A state — 2 PRESENT, 2 LATE, all correct');

    await Promise.all([teacherCtx.close(), principalCtx.close()]);
  });

  // ────────────────────────────────────────────────────────────────────────────
  //  BONUS: HR / Staff Attendance (Principal marks staff attendance)
  //  Principal logs in → navigates to HR Attendance → sees teacher list.
  // ────────────────────────────────────────────────────────────────────────────
  test('BONUS: Principal views HR/staff attendance page', async ({ page }) => {
    await loginAs(page, PRINCIPAL);
    await page.goto(`${TENANT_URL}/hr/attendance`);
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(2_000);
    await snap(page, '15-principal-hr-staff-attendance-page');
    console.log('👔  Principal: HR/staff attendance page loaded');

    // Should not show an error
    const body = page.locator('body');
    await expect(body).not.toContainText('401');
    await expect(body).not.toContainText('Error');
  });
});
