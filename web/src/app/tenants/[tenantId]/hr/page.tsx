import { redirect } from 'next/navigation';

/** /hr → first tab. */
export default function HrIndex({ params }: { params: { tenantId: string } }) {
  redirect(`/tenants/${params.tenantId}/hr/attendance`);
}
