import { redirect } from 'next/navigation';

// Redirect /teachers → /teachers/onboard
export default function TeachersIndex({ params }: { params: { tenantId: string } }) {
  redirect(`/tenants/${params.tenantId}/teachers/onboard`);
}
