package in.schoolapp.branding;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reads and writes the per-school branding override stored under
 * {@code schools.settings.branding} (JSONB). Two consumers:
 *
 * <ul>
 *   <li>The public branding endpoint (web frontend fetches it at boot for runtime overrides).</li>
 *   <li>{@link in.schoolapp.documents.DocumentService} — which injects branding into every
 *       Thymeleaf model so receipts, TCs, hall tickets, and report cards all show the
 *       school's logo + colours + GSTIN + contact block.</li>
 * </ul>
 *
 * <p>The branding object is "best-effort": missing keys → empty strings in the response;
 * frontend/template falls back to its compiled defaults. No errors thrown for partial
 * configs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrandingService {

    /** Key under which the JSON branding object lives in {@code schools.settings}. */
    public static final String SETTINGS_KEY = "branding";

    private final SchoolRepository schoolRepository;

    /** Returns the merged branding (school-level row + JSONB overrides). */
    public BrandingResponse get(UUID schoolId) {
        School s = schoolRepository.findById(schoolId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "School not found"));
        return buildResponse(s);
    }

    /**
     * Patch the branding override. Any non-null/non-blank field on the request replaces the
     * existing value under that key in {@code settings.branding}; null/blank fields are
     * ignored (use the per-field {@code clear-} endpoint to remove a key in the future).
     */
    @Transactional
    public BrandingResponse update(UUID schoolId, BrandingResponse patch) {
        School s = schoolRepository.findById(schoolId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "School not found"));

        Map<String, Object> settings = s.getSettings() != null
            ? new HashMap<>(s.getSettings()) : new HashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> branding = settings.get(SETTINGS_KEY) instanceof Map
            ? new HashMap<>((Map<String, Object>) settings.get(SETTINGS_KEY)) : new HashMap<>();

        // Each field is a no-op when blank; the existing override stays.
        putIfNonBlank(branding, "tagline",         patch.tagline());
        putIfNonBlank(branding, "affiliation",     patch.affiliation());
        putIfNonBlank(branding, "logoUrl",         patch.logoUrl());
        putIfNonBlank(branding, "logoDarkUrl",     patch.logoDarkUrl());
        putIfNonBlank(branding, "faviconUrl",      patch.faviconUrl());
        putIfNonBlank(branding, "loginHeroUrl",    patch.loginHeroUrl());
        putIfNonBlank(branding, "primaryColor",    patch.primaryColor());
        putIfNonBlank(branding, "accentColor",     patch.accentColor());
        putIfNonBlank(branding, "radius",          patch.radius());
        putIfNonBlank(branding, "contactPhone",    patch.contactPhone());
        putIfNonBlank(branding, "contactEmail",    patch.contactEmail());
        putIfNonBlank(branding, "address",         patch.address());
        putIfNonBlank(branding, "websiteUrl",      patch.websiteUrl());
        putIfNonBlank(branding, "gstin",           patch.gstin());
        putIfNonBlank(branding, "socialFacebook",  patch.socialFacebook());
        putIfNonBlank(branding, "socialInstagram", patch.socialInstagram());
        putIfNonBlank(branding, "socialYoutube",   patch.socialYoutube());
        putIfNonBlank(branding, "socialX",         patch.socialX());

        settings.put(SETTINGS_KEY, branding);
        s.setSettings(settings);
        schoolRepository.save(s);
        log.info("Branding updated for school={} keys={}", schoolId, branding.keySet());
        return buildResponse(s);
    }

    /**
     * Returns a Thymeleaf-friendly model fragment for branded PDFs. Document templates
     * reference these keys via {@code ${branding.primaryColor}}, {@code ${branding.logoUrl}}
     * etc.
     */
    public Map<String, Object> brandingModel(UUID schoolId) {
        BrandingResponse r = get(schoolId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schoolName",     r.schoolName());
        out.put("shortName",      r.shortName());
        out.put("tagline",        r.tagline());
        out.put("affiliation",    r.affiliation());
        out.put("logoUrl",        r.logoUrl());
        out.put("primaryColor",   r.primaryColor());
        out.put("accentColor",    r.accentColor());
        out.put("contactPhone",   r.contactPhone());
        out.put("contactEmail",   r.contactEmail());
        out.put("address",        r.address());
        out.put("websiteUrl",     r.websiteUrl());
        out.put("gstin",          r.gstin());
        return out;
    }

    // ---------------- internal ----------------

    @SuppressWarnings("unchecked")
    private BrandingResponse buildResponse(School s) {
        Map<String, Object> branding = s.getSettings() != null
            && s.getSettings().get(SETTINGS_KEY) instanceof Map
            ? (Map<String, Object>) s.getSettings().get(SETTINGS_KEY)
            : Map.of();
        return new BrandingResponse(
            // Identity comes from the row, not the JSONB — schools rename via /school PATCH.
            s.getName(),
            s.getName(),
            str(branding, "tagline"),
            str(branding, "affiliation"),
            str(branding, "logoUrl"),
            str(branding, "logoDarkUrl"),
            str(branding, "faviconUrl"),
            str(branding, "loginHeroUrl"),
            str(branding, "primaryColor"),
            str(branding, "accentColor"),
            str(branding, "radius"),
            firstNonBlank(str(branding, "contactPhone"), s.getPhone()),
            firstNonBlank(str(branding, "contactEmail"), s.getEmail()),
            firstNonBlank(str(branding, "address"),
                joinNonBlank(", ", s.getCity(), s.getState())),
            str(branding, "websiteUrl"),
            str(branding, "gstin"),
            str(branding, "socialFacebook"),
            str(branding, "socialInstagram"),
            str(branding, "socialYoutube"),
            str(branding, "socialX")
        );
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }

    private static void putIfNonBlank(Map<String, Object> m, String key, String value) {
        if (value != null && !value.isBlank()) m.put(key, value);
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c;
        }
        return "";
    }

    private static String joinNonBlank(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }
}
