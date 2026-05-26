package in.schoolapp.infirmary;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.parent.ParentNotificationService;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.infirmary.dto.CreateVisitRequest;
import in.schoolapp.infirmary.dto.MedicalRecordDto;
import in.schoolapp.infirmary.dto.VisitResponse;
import in.schoolapp.infirmary.entity.InfirmaryVisit;
import in.schoolapp.infirmary.entity.StudentMedicalRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InfirmaryService {

    private final InfirmaryVisitRepository visitRepo;
    private final StudentMedicalRecordRepository recordRepo;
    private final ParentNotificationService parentNotificationService;

    @Transactional
    public VisitResponse recordVisit(UUID tenantId, CreateVisitRequest req) {
        InfirmaryVisit v = new InfirmaryVisit();
        v.setSchoolId(tenantId);
        v.setStudentId(req.studentId());
        v.setComplaint(req.complaint());
        v.setTreatment(req.treatment());
        v.setMedicineGiven(req.medicineGiven());
        v.setTemperatureC(req.temperatureC());
        v.setPulse(req.pulse());
        v.setSentHome(req.sentHome());
        v.setRecordedById(TenantContext.getStaffId());

        // Auto-notify the parent if the student was sent home — this is the high-stakes case.
        boolean shouldNotify = req.sentHome();
        if (shouldNotify) {
            String body = String.format(
                "🏥 *Infirmary visit*\n\nComplaint: %s\n%s%s%sStudent has been sent home — please arrange pickup.",
                req.complaint(),
                req.treatment() != null ? "Treatment: " + req.treatment() + "\n" : "",
                req.temperatureC() != null ? "Temperature: " + req.temperatureC() + "°C\n" : "",
                req.medicineGiven() != null ? "Medicine: " + req.medicineGiven() + "\n" : "");
            var outcome = parentNotificationService.notify(
                tenantId, req.studentId(),
                FeatureKey.PARENT_NOTIFY_CIRCULAR,
                "Infirmary visit — pickup requested", body, MessageType.EMERGENCY);
            v.setParentNotified(outcome != ParentNotificationService.Outcome.MASTER_FLAG_OFF
                && outcome != ParentNotificationService.Outcome.CATEGORY_FLAG_OFF
                && outcome != ParentNotificationService.Outcome.NO_CONTACT
                && outcome != ParentNotificationService.Outcome.NO_PRIMARY_PARENT);
        }
        return VisitResponse.from(visitRepo.save(v));
    }

    @Transactional(readOnly = true)
    public Page<VisitResponse> list(UUID tenantId, int page, int size) {
        return visitRepo.findBySchoolIdOrderByVisitedAtDesc(tenantId, PageRequest.of(page, size))
            .map(VisitResponse::from);
    }

    @Transactional(readOnly = true)
    public List<VisitResponse> forStudent(UUID studentId) {
        return visitRepo.findByStudentIdOrderByVisitedAtDesc(studentId).stream()
            .map(VisitResponse::from).toList();
    }

    // ---- Medical record ----

    @Transactional
    public MedicalRecordDto upsertMedical(UUID tenantId, UUID studentId, MedicalRecordDto patch) {
        StudentMedicalRecord r = recordRepo.findById(studentId).orElseGet(() -> {
            StudentMedicalRecord fresh = new StudentMedicalRecord();
            fresh.setStudentId(studentId);
            fresh.setSchoolId(tenantId);
            return fresh;
        });
        r.setBloodGroup(patch.bloodGroup());
        r.setAllergies(patch.allergies());
        r.setChronicConditions(patch.chronicConditions());
        r.setMedications(patch.medications());
        r.setEmergencyContact(patch.emergencyContact());
        r.setEmergencyPhone(patch.emergencyPhone());
        r.setUpdatedAt(OffsetDateTime.now());
        return MedicalRecordDto.from(recordRepo.save(r));
    }

    @Transactional(readOnly = true)
    public MedicalRecordDto getMedical(UUID studentId) {
        return recordRepo.findById(studentId)
            .map(MedicalRecordDto::from)
            .orElseThrow(() -> AppException.notFound(
                ErrorCode.RESOURCE_NOT_FOUND, "StudentMedicalRecord", studentId));
    }
}
