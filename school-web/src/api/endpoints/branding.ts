import { apiGet, apiPut } from '@/api/client';

/**
 * Per-school white-label override payload. Every field is optional on the wire — the backend
 * stores them under {@code schools.settings.branding} and merges with build-time defaults at
 * read time. Sending {@code null} or an empty string clears that override.
 */
export interface BrandingResponse {
  schoolName: string | null;
  shortName: string | null;
  tagline: string | null;
  affiliation: string | null;
  logoUrl: string | null;
  logoDarkUrl: string | null;
  faviconUrl: string | null;
  loginHeroUrl: string | null;
  primaryColor: string | null;
  accentColor: string | null;
  radius: string | null;
  contactPhone: string | null;
  contactEmail: string | null;
  address: string | null;
  websiteUrl: string | null;
  gstin: string | null;
  socialFacebook: string | null;
  socialInstagram: string | null;
  socialYoutube: string | null;
  socialX: string | null;
  // Document signature block — printed on admit cards, report cards and fee receipts.
  signatureUrl: string | null;
  signatoryName: string | null;
  signatoryTitle: string | null;
}

export const brandingApi = {
  /** Public (no-auth) read used by BrandingProvider at boot. */
  getPublic(schoolId: string): Promise<BrandingResponse> {
    return apiGet(`/api/v1/public/schools/${schoolId}/branding`);
  },
  /** OWNER_OR_ADMIN write. Send the full object; backend treats null/blank as "clear". */
  update(tenantId: string, patch: BrandingResponse): Promise<BrandingResponse> {
    return apiPut(`/api/v1/tenants/${tenantId}/branding`, patch);
  },
};
