/**
 * Cosmetic, URL-only slug for a school. It is NEVER used to resolve the tenant — the JWT's
 * `tenantId` claim is authoritative and the API client rewrites the slug to the real UUID. So the
 * slug only needs to be readable; collisions across schools are harmless.
 */
export function slugifySchool(name: string | null | undefined): string {
  const s = (name ?? '')
    .toLowerCase()
    .trim()
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40);
  return s || 'school';
}

/**
 * Resolves a school's slug from its public branding (by real UUID). Falls back to "school" so a
 * login redirect always has a usable, non-UUID path even if branding can't be fetched.
 */
export async function resolveTenantSlug(tenantId: string): Promise<string> {
  try {
    const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? '';
    const res = await fetch(`${base}/api/v1/public/schools/${tenantId}/branding`);
    const json = await res.json();
    const d = json?.data ?? json;
    return slugifySchool(d?.shortName || d?.schoolName);
  } catch {
    return 'school';
  }
}
