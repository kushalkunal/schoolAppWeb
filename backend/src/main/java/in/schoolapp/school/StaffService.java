package in.schoolapp.school;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.EmailNormalizer;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.PhoneNormalizer;
import in.schoolapp.communication.dispatcher.EmailSender;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.dispatcher.WhatsAppNotifier;
import in.schoolapp.hr.entity.LeaveApplication.LeaveType;
import in.schoolapp.hr.entity.LeaveBalance;
import in.schoolapp.hr.repository.LeaveBalanceRepository;
import in.schoolapp.school.dto.CreateStaffRequest;
import in.schoolapp.school.dto.InviteTeacherRequest;
import in.schoolapp.school.dto.StaffResponse;
import in.schoolapp.school.dto.UpdateStaffRequest;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;
import in.schoolapp.school.repository.SchoolRepository;
import in.schoolapp.school.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffService {

    private static final String TEMP_PW_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int TEMP_PW_LEN = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StaffRepository staffRepository;
    private final SchoolRepository schoolRepository;
    private final WhatsAppNotifier whatsAppNotifier;
    private final AuditLogger auditLogger;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final ClassSectionService classSectionService;

    /**
     * Default leave entitlements seeded for every new staff member.
     * HR can adjust individual balances later via the HR → Leave → Balances screen.
     */
    private static final Map<LeaveType, Integer> DEFAULT_LEAVE_DAYS = Map.of(
        LeaveType.CASUAL,    15,
        LeaveType.SICK,      10,
        LeaveType.EARNED,    12,
        LeaveType.MATERNITY, 180,
        LeaveType.PATERNITY, 7,
        LeaveType.COMP_OFF,  0,
        LeaveType.UNPAID,    0,
        LeaveType.OTHER,     0
    );

    @Transactional
    public StaffResponse createStaff(UUID schoolId, CreateStaffRequest req) {
        String phone = PhoneNormalizer.normalize(req.phone());

        // Phone acts as login identifier and must be globally unique (see SchoolService).
        if (staffRepository.existsByPhone(phone)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "A staff member with this phone already exists");
        }

        Staff staff = new Staff();
        staff.setSchoolId(schoolId);
        staff.setFirstName(req.firstName().trim());
        staff.setLastName(blankToNull(req.lastName()));
        staff.setPhone(phone);
        staff.setEmail(blankToNull(req.email()));
        staff.setRole(req.role());
        staff.setActive(true);
        staff = staffRepository.save(staff);
        log.info("Created staff id={} school={} role={}", staff.getId(), schoolId, staff.getRole());
        seedLeaveBalances(schoolId, staff.getId());

        auditLogger.logCreate(schoolId, "Staff", staff.getId(), java.util.Map.of(
            "role", staff.getRole().name(),
            "phoneMasked", PhoneNormalizer.mask(phone),
            "firstName", staff.getFirstName()
        ));
        sendWhatsAppInvite(staff);
        return StaffResponse.from(staff);
    }

    /**
     * Welcome + login instructions for a newly-created staff member. Fire-and-forget — a BSP
     * hiccup must not block the create transaction. The {@link WhatsAppNotifier} decorator
     * writes an audit row + falls back to the logging sender in dev.
     */
    private void sendWhatsAppInvite(Staff staff) {
        if (staff.getPhone() == null || staff.getPhone().isBlank()) return;
        String schoolName = schoolRepository.findById(staff.getSchoolId())
            .map(s -> s.getName())
            .orElse("your school");
        String body = String.format(
            "Welcome to %s, %s! You've been added as %s.\n\n"
                + "To log in, open the app and enter your phone number %s — we'll send you a one-time code.",
            schoolName,
            staff.getFirstName(),
            staff.getRole().name().toLowerCase().replace('_', ' '),
            PhoneNormalizer.mask(staff.getPhone()));
        whatsAppNotifier.send(WhatsAppMessage.text(
            staff.getPhone(), body, MessageType.EMERGENCY,
            WhatsAppMessage.Audit.forSchool(staff.getSchoolId())));
    }

    /**
     * Invite a teacher by email. Admin-only flow:
     * 1. Creates the staff record with email as login identifier.
     * 2. Generates a random temp password, BCrypt-hashes it, sets must_reset_password=true.
     * 3. Sends a welcome email with the temp password (teacher must change on first login).
     */
    @Transactional
    public StaffResponse inviteTeacher(UUID schoolId, InviteTeacherRequest req) {
        if (req.role() != StaffRole.CLASS_TEACHER && req.role() != StaffRole.SUBJECT_TEACHER
                && req.role() != StaffRole.LIBRARIAN) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Only CLASS_TEACHER, SUBJECT_TEACHER, or LIBRARIAN roles can be invited via this endpoint");
        }
        String email = EmailNormalizer.normalize(req.email());
        if (staffRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "A staff member with this email already exists");
        }

        String tempPassword = generateTempPassword();

        Staff staff = new Staff();
        staff.setSchoolId(schoolId);
        staff.setFirstName(req.firstName().trim());
        staff.setLastName(blankToNull(req.lastName()));
        staff.setEmail(email);
        staff.setPhone(blankToNull(req.phone()));
        staff.setRole(req.role());
        staff.setGender(blankToNull(req.gender()));
        staff.setDateOfJoining(req.dateOfJoining());
        if (req.profile() != null) staff.setProfile(req.profile().toMap());
        staff.setActive(true);
        staff.setIdentifierVerified(true);           // email supplied by admin → trusted
        staff.setIdentifierVerifiedAt(OffsetDateTime.now());
        staff.setPasswordHash(passwordEncoder.encode(tempPassword));
        staff.setPasswordSetAt(OffsetDateTime.now());
        staff.setMustResetPassword(true);
        staff = staffRepository.save(staff);
        log.info("Invited teacher id={} school={} role={}", staff.getId(), schoolId, staff.getRole());
        seedLeaveBalances(schoolId, staff.getId());

        auditLogger.logCreate(schoolId, "Staff", staff.getId(), java.util.Map.of(
            "role", staff.getRole().name(),
            "email", email,
            "firstName", staff.getFirstName(),
            "inviteFlow", "email"
        ));

        // One-step registration: optionally make the new teacher a class teacher right away.
        if (req.classTeacherSectionId() != null && req.role() == StaffRole.CLASS_TEACHER) {
            classSectionService.assignClassTeacher(schoolId, req.classTeacherSectionId(), staff.getId());
        }

        sendTeacherInviteEmail(staff, tempPassword, schoolId);
        return StaffResponse.from(staff);
    }

    /**
     * Seed all leave type balances for a newly-created staff member in the current year.
     * Idempotent — skips types that already have a row (e.g. if called twice by mistake).
     */
    private void seedLeaveBalances(UUID schoolId, UUID staffId) {
        int year = LocalDate.now().getYear();
        for (Map.Entry<LeaveType, Integer> entry : DEFAULT_LEAVE_DAYS.entrySet()) {
            boolean exists = leaveBalanceRepository
                .findBySchoolIdAndStaffIdAndLeaveTypeAndYear(schoolId, staffId, entry.getKey(), year)
                .isPresent();
            if (exists) continue;
            LeaveBalance bal = new LeaveBalance();
            bal.setSchoolId(schoolId);
            bal.setStaffId(staffId);
            bal.setLeaveType(entry.getKey());
            bal.setYear(year);
            bal.setEntitledDays(new BigDecimal(entry.getValue()));
            leaveBalanceRepository.save(bal);
        }
        log.info("Seeded leave balances for staff={} school={} year={}", staffId, schoolId, year);
    }

    private void sendTeacherInviteEmail(Staff staff, String tempPassword, UUID schoolId) {
        if (staff.getEmail() == null) return;
        String schoolName = schoolRepository.findById(schoolId)
            .map(s -> s.getName()).orElse("your school");
        String subject = "Welcome to " + schoolName + " — your login details";
        String body = String.format(
            "Hi %s,\n\n"
            + "You have been added as a teacher at %s.\n\n"
            + "Login details:\n"
            + "  Email:    %s\n"
            + "  Password: %s\n\n"
            + "Please sign in and change your password immediately.\n\n"
            + "Regards,\nThe %s Team",
            staff.getFirstName(), schoolName, staff.getEmail(), tempPassword, schoolName);
        try {
            emailSender.send(staff.getEmail(), subject, body);
        } catch (Exception ex) {
            log.warn("Failed to send invite email to teacher={}: {}", staff.getId(), ex.getMessage());
        }
    }

    private static String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PW_LEN);
        for (int i = 0; i < TEMP_PW_LEN; i++) {
            sb.append(TEMP_PW_CHARS.charAt(RANDOM.nextInt(TEMP_PW_CHARS.length())));
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> listStaff(UUID schoolId) {
        return staffRepository.findBySchoolIdAndActiveTrueOrderByFirstName(schoolId).stream()
            .map(StaffResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public StaffResponse getStaff(UUID schoolId, UUID staffId) {
        Staff staff = staffRepository.findById(staffId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Staff", staffId));
        if (!staff.getSchoolId().equals(schoolId)) {
            throw AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Staff", staffId);
        }
        return StaffResponse.from(staff);
    }

    @Transactional
    public StaffResponse updateStaff(UUID schoolId, UUID staffId, UpdateStaffRequest req) {
        Staff staff = staffRepository.findById(staffId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Staff", staffId));
        if (!staff.getSchoolId().equals(schoolId)) {
            throw AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Staff", staffId);
        }
        if (req.firstName() != null && !req.firstName().isBlank()) staff.setFirstName(req.firstName().strip());
        if (req.lastName()  != null)  staff.setLastName(req.lastName().isBlank() ? null : req.lastName().strip());
        if (req.phone()     != null && !req.phone().isBlank())
            staff.setPhone(PhoneNormalizer.normalize(req.phone()));
        if (req.email()     != null)  staff.setEmail(req.email().isBlank() ? null : EmailNormalizer.normalize(req.email()));
        if (req.gender()    != null)  staff.setGender(req.gender().isBlank() ? null : req.gender());
        if (req.dateOfJoining() != null) staff.setDateOfJoining(req.dateOfJoining());
        if (req.role()      != null && staff.getRole() != StaffRole.PRINCIPAL)
            staff.setRole(req.role());
        if (req.profile()   != null) {
            // Merge non-null profile fields onto the existing profile (partial update).
            java.util.Map<String, Object> merged = staff.getProfile() == null
                ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(staff.getProfile());
            merged.putAll(req.profile().toMap());
            staff.setProfile(merged);
        }
        staff = staffRepository.save(staff);
        auditLogger.logUpdate(schoolId, "Staff", staffId,
            java.util.Map.of("name", (Object) staff.displayName()),
            java.util.Map.of("name", (Object) staff.displayName()));
        return StaffResponse.from(staff);
    }

    /** Soft-delete by deactivating rather than removing — preserves audit/history. */
    @Transactional
    public void deactivateStaff(UUID schoolId, UUID staffId) {
        Staff staff = staffRepository.findById(staffId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Staff", staffId));
        if (!staff.getSchoolId().equals(schoolId)) {
            throw AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Staff", staffId);
        }
        if (staff.getRole() == StaffRole.PRINCIPAL) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Principal cannot be deactivated via this endpoint");
        }
        staff.setActive(false);
        staffRepository.save(staff);
        auditLogger.logDelete(schoolId, "Staff", staffId, java.util.Map.of(
            "role", staff.getRole().name(),
            "name", staff.displayName()
        ));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
