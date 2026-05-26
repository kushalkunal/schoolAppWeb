package in.schoolapp.school;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.PhoneNormalizer;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.dispatcher.WhatsAppNotifier;
import in.schoolapp.school.dto.CreateStaffRequest;
import in.schoolapp.school.dto.StaffResponse;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;
import in.schoolapp.school.repository.SchoolRepository;
import in.schoolapp.school.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository staffRepository;
    private final SchoolRepository schoolRepository;
    private final WhatsAppNotifier whatsAppNotifier;
    private final AuditLogger auditLogger;

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

    @Transactional(readOnly = true)
    public List<StaffResponse> listStaff(UUID schoolId) {
        return staffRepository.findBySchoolIdAndActiveTrueOrderByFirstName(schoolId).stream()
            .map(StaffResponse::from)
            .toList();
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
