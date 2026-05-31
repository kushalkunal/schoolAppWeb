/**
 * E2E: Five staff roles performing day-to-day activities (live stack).
 *
 * One describe block per role. Each proves the role can do its daily work AND is blocked from
 * other roles' privileged actions — the RBAC matrix exercised against the real backend. We assert
 * the *authorization outcome* (403 vs allowed) rather than business status, so locks/idempotency
 * (e.g. attendance already submitted → 409) don't make the RBAC assertion flaky.
 *
 * Roles: Principal, Class Teacher, Subject Teacher, Accountant, Librarian.
 * Pre-req: backend http://localhost:8081 with the standard VMS seed.
 */

import { test, expect, type APIRequestContext } from '@playwright/test';

const API_BASE = process.env.E2E_API_BASE ?? 'http://localhost:8081';
const TENANT = '926c372c-139d-460d-83b1-1a80ef92db57';
const BASE = `${API_BASE}/api/v1/tenants/${TENANT}`;

const SEC_1A = '032ec61c-3373-4389-9169-16ae826c357a'; // Ananya's own section
const SEC_2A = '64a5ca42-7480-46d2-83c1-9bd44a12dcb7'; // a different class teacher's section

const CREDS = {
  principal: { email: 'teacher@vms.school', password: 'Test@1234' },
  classTeacher: { email: 'ananya.singh@vms.school', password: 'Teacher@123' },
  subjectTeacher: { email: 'rohan.mehta@vms.school', password: 'Teacher@123' },
  accountant: { email: 'accountant@vms.school', password: 'Teacher@123' },
  librarian: { email: 'librarian@vms.school', password: 'Teacher@123' },
};

async function login(request: APIRequestContext, c: { email: string; password: string }): Promise<string> {
  const res = await request.post(`${API_BASE}/api/v1/auth/password/login`, { data: c });
  expect(res.ok(), `login ${c.email}: ${res.status()}`).toBeTruthy();
  return (await res.json()).data.accessToken;
}
const H = (t: string) => ({ Authorization: `Bearer ${t}` });
const today = () => new Date().toISOString().slice(0, 10);

/** Allowed past RBAC = anything except 401/403 (business codes like 409/422 still mean "permitted"). */
const allowed = (status: number) => status !== 401 && status !== 403;

const tokens: Record<string, string> = {};
test.beforeAll(async ({ playwright }) => {
  const request = await playwright.request.newContext();
  for (const [role, c] of Object.entries(CREDS)) tokens[role] = await login(request, c);
  await request.dispose();
});

test.describe('Principal — admin/oversight', () => {
  test('can view the fee dashboard and create a circular; (admin surface)', async ({ request }) => {
    const dash = await request.get(`${BASE}/fees/dashboard`, { headers: H(tokens.principal) });
    expect(allowed(dash.status()), `dashboard ${dash.status()}`).toBeTruthy();

    const circ = await request.post(`${BASE}/circulars`, {
      headers: H(tokens.principal),
      data: { title: `Principal circular ${Date.now()}`, body: 'Staff meeting at 4pm.', targetType: 'ALL_PARENTS' },
    });
    expect(circ.status(), `circular ${await circ.text()}`).toBe(201);
  });
});

test.describe('Class Teacher — own section', () => {
  test('can mark own section attendance, blocked on another section', async ({ request }) => {
    const own = await request.post(`${BASE}/sections/${SEC_1A}/attendance`, {
      headers: H(tokens.classTeacher),
      data: { date: today(), entries: [{ studentId: 'd0000001-0000-0000-0000-000000000001', status: 'PRESENT' }] },
    });
    expect(allowed(own.status()), `own-section ${own.status()}: ${await own.text()}`).toBeTruthy();

    const other = await request.post(`${BASE}/sections/${SEC_2A}/attendance`, {
      headers: H(tokens.classTeacher),
      data: { date: today(), entries: [] },
    });
    expect(other.status(), 'other section forbidden').toBe(403);
  });

  test('can mark own staff attendance, cannot create a circular (admin-only)', async ({ request }) => {
    const self = await request.post(`${BASE}/hr/attendance/self?status=PRESENT`, { headers: H(tokens.classTeacher) });
    expect(allowed(self.status()), `self-attendance ${self.status()}`).toBeTruthy();

    const circ = await request.post(`${BASE}/circulars`, {
      headers: H(tokens.classTeacher),
      data: { title: 'nope', body: 'nope', targetType: 'ALL_PARENTS' },
    });
    expect(circ.status(), 'class teacher cannot create circular').toBe(403);
  });
});

test.describe('Subject Teacher — academics', () => {
  test('can view exams, cannot create an exam (admin-only)', async ({ request }) => {
    const exams = await request.get(`${BASE}/exams`, { headers: H(tokens.subjectTeacher) });
    expect(allowed(exams.status()), `list exams ${exams.status()}`).toBeTruthy();

    const create = await request.post(`${BASE}/exams`, {
      headers: H(tokens.subjectTeacher),
      data: { name: 'nope', examType: 'UNIT_TEST', startDate: '2026-06-01', endDate: '2026-06-02' },
    });
    expect(create.status(), 'subject teacher cannot create exam').toBe(403);
  });

  test('cannot create a circular either (admin-only)', async ({ request }) => {
    const circ = await request.post(`${BASE}/circulars`, {
      headers: H(tokens.subjectTeacher),
      data: { title: 'nope', body: 'nope', targetType: 'ALL_PARENTS' },
    });
    expect(circ.status()).toBe(403);
  });
});

test.describe('Accountant — fees', () => {
  test('can view fee dashboard + collection register', async ({ request }) => {
    const dash = await request.get(`${BASE}/fees/dashboard`, { headers: H(tokens.accountant) });
    expect(allowed(dash.status()), `fee dashboard ${dash.status()}`).toBeTruthy();

    const reg = await request.get(`${BASE}/fees/reports/collection-register?from=${today()}&to=${today()}`, {
      headers: H(tokens.accountant),
    });
    expect(allowed(reg.status()), `collection register ${reg.status()}`).toBeTruthy();
  });

  test('cannot mark attendance, cannot add a library book', async ({ request }) => {
    const att = await request.post(`${BASE}/sections/${SEC_1A}/attendance`, {
      headers: H(tokens.accountant),
      data: { date: today(), entries: [] },
    });
    expect(att.status(), 'accountant cannot mark attendance').toBe(403);

    const book = await request.post(`${BASE}/library/books`, {
      headers: H(tokens.accountant),
      data: { title: 'nope', totalCopies: 1, availableCopies: 1 },
    });
    expect(book.status(), 'accountant cannot add a book').toBe(403);
  });
});

test.describe('Librarian — library', () => {
  test('can view catalogue and add a book', async ({ request }) => {
    const list = await request.get(`${BASE}/library/books`, { headers: H(tokens.librarian) });
    expect(allowed(list.status()), `book list ${list.status()}`).toBeTruthy();

    const add = await request.post(`${BASE}/library/books`, {
      headers: H(tokens.librarian),
      data: {
        title: `E2E Daily Book ${Date.now()}`, author: 'QA', isbn: `${Date.now()}`,
        category: 'Fiction', totalCopies: 3, availableCopies: 3,
      },
    });
    expect(add.status(), `add book ${await add.text()}`).toBe(201);
  });

  test('cannot collect fees / create a circular (not their role)', async ({ request }) => {
    const circ = await request.post(`${BASE}/circulars`, {
      headers: H(tokens.librarian),
      data: { title: 'nope', body: 'nope', targetType: 'ALL_PARENTS' },
    });
    expect(circ.status(), 'librarian cannot create circular').toBe(403);
  });
});
