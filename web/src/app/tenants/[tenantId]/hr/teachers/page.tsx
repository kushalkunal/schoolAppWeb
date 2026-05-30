import { redirect } from 'next/navigation';

/** /hr/teachers → /hr/teachers/onboard (staff list + detail drawer) */
export default function HrTeachersIndex({ params }: { params: { tenantId: string } }) {
  redirect(`/tenants/${params.tenantId}/hr/teachers/onboard`);
}
