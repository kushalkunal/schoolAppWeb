package in.schoolapp.branding;

/**
 * Per-school white-label override payload.
 *
 * <p>Returned by the public {@code GET /api/v1/public/schools/{id}/branding} endpoint
 * (no auth — schools embed the URL in their public marketing site frontend deploys).
 * Also injected into every PDF document model so receipts / TCs / hall tickets carry
 * the school's logo + colours.
 *
 * <p>Every field is optional: the frontend / template falls back to its compiled-in
 * defaults when the override is null/blank.
 */
public record BrandingResponse(
    String schoolName,
    String shortName,
    String tagline,
    String affiliation,
    String logoUrl,
    String logoDarkUrl,
    String faviconUrl,
    String loginHeroUrl,
    String primaryColor,
    String accentColor,
    String radius,
    String contactPhone,
    String contactEmail,
    String address,
    String websiteUrl,
    String gstin,
    String socialFacebook,
    String socialInstagram,
    String socialYoutube,
    String socialX
) {}
