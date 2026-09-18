'use client';

import { useQuery } from '@tanstack/react-query';
import { studentsApi } from '@/api/endpoints/students';
import { EntityPicker, type PickerOption } from './EntityPicker';

/**
 * Search-as-you-type combobox over the tenant's active students. Reads via
 * {@link studentsApi.list} — pages are cached per-tenant by react-query so multiple
 * pickers on a page share the same fetch.
 */
export function StudentPicker({
  tenantId, value, onChange, label = 'Student', required, disabled,
}: {
  tenantId: string;
  value: string | null;
  onChange: (id: string | null) => void;
  label?: string;
  required?: boolean;
  disabled?: boolean;
}) {
  const q = useQuery({
    queryKey: ['students-list-for-picker', tenantId],
    queryFn: () => studentsApi.list(tenantId, { size: 500 }),
    staleTime: 60_000,
  });

  const options: PickerOption[] = (q.data?.items ?? []).map((s) => ({
    id: s.id,
    label: s.displayName,
    sub: s.admissionNumber ? `Adm #${s.admissionNumber}` : null,
  }));

  return (
    <EntityPicker
      label={label}
      value={value}
      onChange={onChange}
      options={options}
      placeholder={q.isLoading ? 'Loading students…' : 'Search by name or admission #'}
      required={required}
      disabled={disabled || q.isLoading}
      emptyText="No matching student"
      loading={q.isLoading}
    />
  );
}
