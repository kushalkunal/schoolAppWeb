package in.schoolapp.attendance;

import in.schoolapp.attendance.entity.AttendanceRecord;
import in.schoolapp.attendance.event.AttendanceSubmittedEvent;
import in.schoolapp.communication.dispatcher.EmailSender;
import in.schoolapp.communication.dispatcher.WhatsAppMessage;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.dispatcher.WhatsAppNotifier;
import in.schoolapp.communication.template.MessageTemplateService;
import in.schoolapp.feature.FeatureFlagService;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.entity.School;
import in.schoolapp.student.FamilyService;
import in.schoolapp.student.entity.Parent;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sibling-aware absence alerts (gap analysis §5.6). When two students share a primary parent,
 * one combined message is sent instead of N separate ones.
 * <p>
 * Runs on the {@code notificationExecutor} thread pool so the attendance HTTP response returns
 * before messages are dispatched. The WhatsApp call itself is delegated to
 * {@link WhatsAppNotifier} — swapping the logging stub for a real WATI client requires no
 * changes here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbsenceAlertService {

    private final FamilyService familyService;
    private final StudentRepository studentRepository;
    private final SchoolService schoolService;
    private final MessageTemplateService templates;
    private final WhatsAppNotifier whatsAppNotifier;
    private final EmailSender emailSender;
    private final FeatureFlagService featureFlagService;

    @Async("notificationExecutor")
    @EventListener
    public void onAttendanceSubmitted(AttendanceSubmittedEvent event) {
        if (event.absentRecords().isEmpty() && event.lateRecords().isEmpty()) {
            return;
        }
        // Slice 33 — master switch gate. If parent notifications are off for this tenant,
        // skip the whole event regardless of category. Per-category flags below.
        if (!featureFlagService.isEnabled(event.tenantId(), FeatureKey.PARENT_NOTIFICATIONS)) {
            return;
        }
        boolean absenceOn = featureFlagService.isEnabled(event.tenantId(), FeatureKey.PARENT_NOTIFY_ABSENCE);
        boolean lateOn = featureFlagService.isEnabled(event.tenantId(), FeatureKey.PARENT_NOTIFY_LATE_ARRIVAL);
        if (!absenceOn && !lateOn) return;

        School school = schoolService.getSchoolEntity(event.tenantId());

        List<Student> absentStudents = absenceOn ? loadStudents(event.absentRecords()) : List.of();
        List<Student> lateStudents = lateOn ? loadStudents(event.lateRecords()) : List.of();

        familyService.groupStudentsByPrimaryParent(absentStudents)
            .forEach((parentId, children) -> dispatchAbsence(school, children, event));

        familyService.groupStudentsByPrimaryParent(lateStudents)
            .forEach((parentId, children) -> dispatchLate(school, children, event));

        warnOnOrphanedAlerts(event.tenantId(), absentStudents, "absent");
        warnOnOrphanedAlerts(event.tenantId(), lateStudents, "late");
    }

    private void dispatchAbsence(School school, List<Student> children, AttendanceSubmittedEvent event) {
        Optional<Parent> parentOpt = familyService.getPrimaryParent(children.get(0).getId());
        if (parentOpt.isEmpty() || parentOpt.get().getPhone() == null) return;
        Parent parent = parentOpt.get();

        String body = children.size() == 1
            ? templates.absenceAlert(school.getName(), parent.getName(), children.get(0), event.date())
            : templates.siblingAbsenceAlert(school.getName(), parent.getName(), children, event.date());

        // Single row per family for sibling-aware alerts — studentId tracked is the first child.
        WhatsAppMessage.Audit audit = WhatsAppMessage.Audit.forParent(
            school.getId(), children.get(0).getId(), parent.getId(), parent.getName());
        whatsAppNotifier.send(WhatsAppMessage.text(
            parent.getPhone(), body, MessageType.ABSENCE_ALERT, audit));
        // Slice 33 — also mirror to email if the parent has one. Subject is human-friendly
        // ("ABCD School — Absence alert"); body reuses the same template.
        if (parent.getEmail() != null && !parent.getEmail().isBlank()) {
            try {
                emailSender.send(parent.getEmail(),
                    school.getName() + " — Absence alert", body);
            } catch (Exception e) {
                log.warn("Absence email failed parent={} — {}", parent.getId(), e.getMessage());
            }
        }
    }

    private void dispatchLate(School school, List<Student> children, AttendanceSubmittedEvent event) {
        Optional<Parent> parentOpt = familyService.getPrimaryParent(children.get(0).getId());
        if (parentOpt.isEmpty() || parentOpt.get().getPhone() == null) return;
        Parent parent = parentOpt.get();

        // Late alerts are always per-child — parents expect specifics (which child, what time).
        for (Student child : children) {
            String body = templates.lateArrivalAlert(
                school.getName(), parent.getName(), child, event.date());
            WhatsAppMessage.Audit audit = WhatsAppMessage.Audit.forParent(
                school.getId(), child.getId(), parent.getId(), parent.getName());
            whatsAppNotifier.send(WhatsAppMessage.text(
                parent.getPhone(), body, MessageType.LATE_ARRIVAL_ALERT, audit));
            if (parent.getEmail() != null && !parent.getEmail().isBlank()) {
                try {
                    emailSender.send(parent.getEmail(),
                        school.getName() + " — Late arrival", body);
                } catch (Exception e) {
                    log.warn("Late-arrival email failed parent={} — {}", parent.getId(), e.getMessage());
                }
            }
        }
    }

    private List<Student> loadStudents(List<AttendanceRecord> records) {
        if (records.isEmpty()) return List.of();
        List<UUID> ids = records.stream().map(AttendanceRecord::getStudentId).toList();
        List<Student> loaded = new ArrayList<>();
        studentRepository.findAllById(ids).forEach(loaded::add);
        return loaded;
    }

    private void warnOnOrphanedAlerts(UUID tenantId, List<Student> students, String kind) {
        long orphaned = students.stream()
            .filter(s -> familyService.getPrimaryParentId(s.getId()) == null)
            .count();
        if (orphaned > 0) {
            log.warn("Skipped {} {} alert(s) — no primary parent configured tenantId={}",
                orphaned, kind, tenantId);
        }
    }
}
