'use client';

import { useQuery } from '@tanstack/react-query';
import { schoolApi } from '@/api/endpoints/school';
import { EntityPicker, type PickerOption } from './EntityPicker';

/**
 * Search-as-you-type combobox over the tenant's active staff. Pages reach for this
 * instead of asking the user to paste a staffId UUID.
 */
export function StaffPicker({
  tenantId, value, onChange, label = 'Teacher / Staff', required, disabled, rolesFilter,
}: {
  tenantId: string;
  value: string | null;
  onChange: (id: string | null) => void;
  label?: string;
  required?: boolean;
  disabled?: boolean;
  /** Optional whitelist of roles to show (e.g. ['CLASS_TEACHER','SUBJECT_TEACHER']). */
  rolesFilter?: string[];
}) {
  const q = useQuery({
    queryKey: ['staff-list-for-picker', tenantId],
    queryFn: () => schoolApi.listStaff(tenantId),
    staleTime: 60_000,
  });

  const filtered = (q.data ?? []).filter((s) => s.active && (!rolesFilter || rolesFilter.includes(s.role)));
  const options: PickerOption[] = filtered.map((s) => ({
    id: s.id,
    label: s.displayName,
    sub: prettyRole(s.role),
  }));

  return (
    <EntityPicker
      label={label}
      value={value}
      onChange={onChange}
      options={options}
      placeholder={q.isLoading ? 'Loading staff…' : 'Search by name'}
      required={required}
      disabled={disabled || q.isLoading}
      emptyText="No matching staff"
      loading={q.isLoading}
    />
  );
}

function prettyRole(role: string): string {
  return role.replace(/_/g, ' ').toLowerCase().replace(/(^|\s)\w/g, (m) => m.toUpperCase());
}
