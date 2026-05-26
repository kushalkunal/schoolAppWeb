import { apiClient, apiDelete, apiGet, apiPost } from '@/api/client';

export interface BookDto {
  id: string;
  title: string;
  author: string | null;
  isbn: string | null;
  publisher: string | null;
  category: string | null;
  totalCopies: number;
  availableCopies: number;
}

export interface IssueDto {
  id: string;
  bookId: string;
  studentId: string;
  issuedAt: string;
  dueDate: string;
  returnedAt: string | null;
  finePaise: number;
  note: string | null;
}

export interface CreateBookRequest {
  title: string;
  author?: string;
  isbn?: string;
  publisher?: string;
  category?: string;
  totalCopies: number;
  availableCopies: number;
}

export const libraryApi = {
  // ---------- Books ----------
  listBooks(tenantId: string): Promise<BookDto[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/library/books`);
  },
  createBook(tenantId: string, req: CreateBookRequest): Promise<BookDto> {
    return apiPost(`/api/v1/tenants/${tenantId}/library/books`, req);
  },
  deactivateBook(tenantId: string, bookId: string): Promise<void> {
    return apiDelete(`/api/v1/tenants/${tenantId}/library/books/${bookId}`);
  },

  // ---------- Issues ----------
  issue(tenantId: string, bookId: string, studentId: string, dueDate?: string, note?: string)
  : Promise<IssueDto> {
    const q = new URLSearchParams({ bookId, studentId });
    if (dueDate) q.set('dueDate', dueDate);
    if (note) q.set('note', note);
    return apiClient.post(`/api/v1/tenants/${tenantId}/library/issues?${q}`)
      .then((r) => r.data.data as IssueDto);
  },
  returnBook(tenantId: string, issueId: string): Promise<IssueDto> {
    return apiPost(`/api/v1/tenants/${tenantId}/library/issues/${issueId}/return`);
  },
  outstanding(tenantId: string): Promise<IssueDto[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/library/issues/outstanding`);
  },
  forStudent(tenantId: string, studentId: string): Promise<IssueDto[]> {
    return apiGet(`/api/v1/tenants/${tenantId}/library/students/${studentId}/issues`);
  },
};
