import { redirect } from 'next/navigation';

export default function LibraryIndex({ params }: { params: { tenantId: string } }) {
  redirect(`/tenants/${params.tenantId}/library/books`);
}
