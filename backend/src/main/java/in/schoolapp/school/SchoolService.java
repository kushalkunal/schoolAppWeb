package in.schoolapp.school;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.auth.config.SignupChannel;
import in.schoolapp.auth.config.SignupProperties;
import in.schoolapp.billing.SubscriptionService;
import in.schoolapp.common.AppException;
import in.schoolapp.common.EmailNormalizer;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.PhoneNormalizer;
import in.schoolapp.school.dto.AcademicYearResponse;
import in.schoolapp.school.dto.CreateSchoolRequest;
import in.schoolapp.school.dto.SchoolResponse;
import in.schoolapp.school.dto.SchoolSignupResponse;
import in.schoolapp.school.dto.StaffResponse;
import in.schoolapp.school.dto.UpdateSchoolRequest;
import in.schoolapp.school.entity.AcademicYear;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;
import in.schoolapp.school.repository.SchoolRepository;
import in.schoolapp.school.repository.StaffRepository;
import in.schoolapp.storage.FileStorageService;
import in.schoolapp.storage.dto.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolService {

    private final SchoolRepository schoolRepository;
    private final StaffRepository staffRepository;
    private final AcademicYearService academicYearService;
    private final SignupProperties signupProps;
    private final FileStorageService fileStorageService;
    private final AuditLogger auditLogger;
    private final SubscriptionService subscriptionService;

    /**
     * Public signup: creates the School, its Principal as a Staff record (the login subject),
     * and the current academic year — all in one transaction. Which identifier(s) are accepted
     * depends on {@code app.signup.channel}:
     * <ul>
     *   <li>{@code PHONE}  — phone required, email optional</li>
     *   <li>{@code EMAIL}  — email required, phone optional</li>
     *   <li>{@code BOTH}   — at least one of phone/email required</li>
     * </ul>
     * Identifiers used must be globally unique across schools and staff.
     */
    @Transactional
    public SchoolSignupResponse createSchool(CreateSchoolRequest req) {
        SignupChannel channel = signupProps.channel();
        Identifiers ids = resolveIdentifiers(channel, req.phone(), req.email());

        if (ids.phone() != null && (schoolRepository.existsByPhone(ids.phone())
                || staffRepository.existsByPhone(ids.phone()))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "This phone number is already in use");
        }
        if (ids.email() != null && (schoolRepository.existsByEmail(ids.email())
                || staffRepository.existsByEmail(ids.email()))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "This email is already in use");
        }

        School school = new School();
        school.setName(req.schoolName().trim());
        school.setPrincipalName(req.principalName().trim());
        school.setPhone(ids.phone());
        school.setEmail(ids.email());
        school.setState(req.state().trim());
        school.setCity(blankToNull(req.city()));
        school.setBoard(req.board());
        school.setSettings(initialSettings());
        school = schoolRepository.save(school);
        log.info("Created school id={} name='{}' board={} signupChannel={}",
            school.getId(), school.getName(), school.getBoard(), channel);

        Staff principal = createPrincipal(school.getId(), req.principalName(), ids.phone(), ids.email());
        AcademicYear year = academicYearService.createCurrentYearForSchool(school.getId());

        // Start the SaaS subscription clock — every new tenant lands on the FREE plan in TRIAL.
        // SubscriptionService writes its own audit row; SchoolService remains the single
        // entry point for "a school becomes live in our system".
        subscriptionService.startTrialForSchool(school.getId());

        String nextStep = ids.email() != null ? "SEND_EMAIL_OTP" : "SEND_PHONE_OTP";

        return new SchoolSignupResponse(
            SchoolResponse.from(school),
            StaffResponse.from(principal),
            AcademicYearResponse.from(year),
            nextStep
        );
    }

    public SchoolResponse getSchool(UUID schoolId) {
        return SchoolResponse.from(getSchoolEntity(schoolId));
    }

    public School getSchoolEntity(UUID schoolId) {
        return schoolRepository.findById(schoolId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.SCHOOL_NOT_FOUND, "School", schoolId));
    }

    /**
     * All schools known to the platform — used by per-tenant batch jobs (fee reminders,
     * library overdue cron). Not paginated because the row count is in the low thousands at
     * worst; if that ever changes, switch to a streaming query.
     */
    public java.util.List<School> findAllActiveSchools() {
        return schoolRepository.findAll();
    }

    /**
     * Patch-style update — only non-null fields in the request are applied. Identifier changes
     * (phone / email / board) are NOT accepted here; those need a re-verification flow because
     * they affect login.
     */
    @Transactional
    public SchoolResponse updateSchool(UUID schoolId, UpdateSchoolRequest req) {
        School s = getSchoolEntity(schoolId);
        Map<String, Object> oldValues = new HashMap<>();
        Map<String, Object> newValues = new HashMap<>();

        applyIfChanged("name", s.getName(), req.name(), s::setName, oldValues, newValues);
        applyIfChanged("principalName", s.getPrincipalName(), req.principalName(),
            s::setPrincipalName, oldValues, newValues);
        applyIfChanged("address", s.getAddress(), req.address(), s::setAddress, oldValues, newValues);
        applyIfChanged("city", s.getCity(), req.city(), s::setCity, oldValues, newValues);
        applyIfChanged("state", s.getState(), req.state(), s::setState, oldValues, newValues);
        applyIfChanged("pincode", s.getPincode(), req.pincode(), s::setPincode, oldValues, newValues);
        applyIfChanged("whatsappNumber", s.getWhatsappNumber(), req.whatsappNumber(),
            s::setWhatsappNumber, oldValues, newValues);

        if (!newValues.isEmpty()) {
            schoolRepository.save(s);
            auditLogger.logUpdate(schoolId, "School", schoolId, oldValues, newValues);
            log.info("Updated school id={} fields={}", schoolId, newValues.keySet());
        }
        return SchoolResponse.from(s);
    }

    /**
     * Uploads (or replaces) the school logo. Stored at a deterministic key so subsequent
     * uploads overwrite — no accumulation of orphaned logos. Returns the fresh {@link SchoolResponse}
     * with {@code logoUrl} populated.
     */
    @Transactional
    public SchoolResponse uploadLogo(UUID schoolId, byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Logo file is empty");
        }
        School s = getSchoolEntity(schoolId);
        String ext = extensionFor(contentType);
        String key = "logos/" + schoolId + "/logo" + ext;
        StoredFile stored = fileStorageService.store(key, bytes,
            contentType == null ? "application/octet-stream" : contentType);
        String oldUrl = s.getLogoUrl();
        s.setLogoUrl(stored.url());
        schoolRepository.save(s);
        auditLogger.logUpdate(schoolId, "School", schoolId,
            Map.of("logoUrl", oldUrl == null ? "null" : oldUrl),
            Map.of("logoUrl", stored.url(), "sizeBytes", bytes.length));
        log.info("Updated school logo id={} key={} bytes={}", schoolId, key, bytes.length);
        return SchoolResponse.from(s);
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) return ".bin";
        String ct = contentType.toLowerCase();
        if (ct.contains("png")) return ".png";
        if (ct.contains("jpeg") || ct.contains("jpg")) return ".jpg";
        if (ct.contains("webp")) return ".webp";
        if (ct.contains("svg")) return ".svg";
        return ".bin";
    }

    private static <T> void applyIfChanged(String field, T current, T incoming,
                                           java.util.function.Consumer<T> setter,
                                           Map<String, Object> oldValues,
                                           Map<String, Object> newValues) {
        if (incoming == null) return;  // null = caller did not provide the field
        if (incoming.equals(current)) return;
        oldValues.put(field, current == null ? "null" : current);
        newValues.put(field, incoming);
        setter.accept(incoming);
    }

    private Identifiers resolveIdentifiers(SignupChannel channel, String rawPhone, String rawEmail) {
        boolean hasPhone = rawPhone != null && !rawPhone.isBlank();
        boolean hasEmail = rawEmail != null && !rawEmail.isBlank();

        return switch (channel) {
            case PHONE -> {
                if (!hasPhone) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Phone number is required (signup channel: PHONE)");
                }
                yield new Identifiers(PhoneNormalizer.normalize(rawPhone),
                    hasEmail ? EmailNormalizer.normalize(rawEmail) : null);
            }
            case EMAIL -> {
                if (!hasEmail) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Email is required (signup channel: EMAIL)");
                }
                yield new Identifiers(hasPhone ? PhoneNormalizer.normalize(rawPhone) : null,
                    EmailNormalizer.normalize(rawEmail));
            }
            case BOTH -> {
                if (!hasPhone && !hasEmail) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Either phone or email is required");
                }
                yield new Identifiers(
                    hasPhone ? PhoneNormalizer.normalize(rawPhone) : null,
                    hasEmail ? EmailNormalizer.normalize(rawEmail) : null);
            }
        };
    }

    private Staff createPrincipal(UUID schoolId, String displayName, String phone, String email) {
        String[] parts = displayName.trim().split("\\s+", 2);
        Staff principal = new Staff();
        principal.setSchoolId(schoolId);
        principal.setFirstName(parts[0]);
        principal.setLastName(parts.length > 1 ? parts[1] : null);
        principal.setPhone(phone);
        principal.setEmail(email);
        principal.setRole(StaffRole.PRINCIPAL);
        principal.setActive(true);
        return staffRepository.save(principal);
    }

    private Map<String, Object> initialSettings() {
        Map<String, Object> onboarding = new HashMap<>();
        onboarding.put("schoolInfoComplete", true);
        onboarding.put("classesCreated", false);
        onboarding.put("studentsAdded", false);
        onboarding.put("staffAdded", false);
        onboarding.put("whatsappConnected", false);

        Map<String, Object> settings = new HashMap<>();
        settings.put("onboarding", onboarding);
        settings.put("receiptSequence", 0);
        settings.put("minAttendancePct", 75);
        return settings;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private record Identifiers(String phone, String email) {}
}
