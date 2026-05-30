package in.schoolapp.ptm;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.communication.dispatcher.WhatsAppMessage.MessageType;
import in.schoolapp.communication.parent.ParentNotificationService;
import in.schoolapp.feature.FeatureKey;
import in.schoolapp.ptm.entity.PtmBooking;
import in.schoolapp.ptm.entity.PtmSlot;
import in.schoolapp.student.StudentAccessGuard;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PtmService {

    private final PtmSlotRepository slotRepo;
    private final PtmBookingRepository bookingRepo;
    private final ParentNotificationService parentNotificationService;
    private final StudentEnrollmentRepository enrollmentRepo;
    private final StudentAccessGuard studentAccessGuard;

    @Transactional
    public PtmSlot createSlot(UUID tenantId, UUID teacherId, UUID sectionId,
                              LocalDate date, LocalTime start, LocalTime end, int capacity) {
        PtmSlot s = new PtmSlot();
        s.setSchoolId(tenantId);
        s.setTeacherId(teacherId);
        s.setSectionId(sectionId);          // nullable — null means open/unclassed slot
        s.setSlotDate(date);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setCapacity(Math.max(1, capacity));
        PtmSlot saved = slotRepo.save(s);

        // Broadcast announcement to all parents in the class when section is specified
        if (sectionId != null) {
            List<StudentEnrollment> enrollments =
                enrollmentRepo.findBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE);
            String body = String.format(
                "📢 *Parent-Teacher Meeting Announcement*\n\nA PTM has been scheduled.\n\n" +
                "📅 Date: %s\n🕐 Time: %s – %s\n\nKindly book your slot with the school.",
                date, start.toString().substring(0, 5), end.toString().substring(0, 5));
            for (StudentEnrollment enr : enrollments) {
                try {
                    parentNotificationService.notify(
                        tenantId, enr.getStudentId(), FeatureKey.PARENT_NOTIFY_CIRCULAR,
                        "PTM scheduled on " + date, body, MessageType.CIRCULAR);
                } catch (Exception ex) {
                    // best-effort: log and continue so one bad parent doesn't abort the rest
                }
            }
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PtmSlot> slotsForDate(UUID tenantId, LocalDate date) {
        return slotRepo.findBySchoolIdAndSlotDateOrderByStartTime(tenantId, date);
    }

    @Transactional
    public PtmBooking book(UUID tenantId, UUID slotId, UUID studentId, String notes) {
        studentAccessGuard.assertInTenant(tenantId, studentId);
        PtmSlot s = slotRepo.findByIdAndSchoolId(slotId, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "PtmSlot", slotId));
        if (s.getBookedCount() >= s.getCapacity()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Slot is fully booked");
        }
        PtmBooking b = new PtmBooking();
        b.setSlotId(slotId);
        b.setSchoolId(tenantId);
        b.setStudentId(studentId);
        b.setNotes(notes);
        PtmBooking saved = bookingRepo.save(b);
        s.setBookedCount(s.getBookedCount() + 1);
        slotRepo.save(s);

        // Notify parent that the PTM is confirmed.
        String body = String.format(
            "📅 *Parent-Teacher Meeting confirmed*\n\nDate: %s\nTime: %s – %s\n\nPlease arrive 5 minutes early.",
            s.getSlotDate(), s.getStartTime(), s.getEndTime());
        parentNotificationService.notify(
            tenantId, studentId, FeatureKey.PARENT_NOTIFY_CIRCULAR,
            "PTM confirmed for " + s.getSlotDate(), body, MessageType.CIRCULAR);
        return saved;
    }

    @Transactional
    public void cancel(UUID tenantId, UUID bookingId) {
        PtmBooking b = bookingRepo.findByIdAndSchoolId(bookingId, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "PtmBooking", bookingId));
        if (!"CONFIRMED".equals(b.getStatus())) return;
        b.setStatus("CANCELLED");
        bookingRepo.save(b);
        slotRepo.findById(b.getSlotId()).ifPresent(s -> {
            s.setBookedCount(Math.max(0, s.getBookedCount() - 1));
            slotRepo.save(s);
        });
    }

    @Transactional(readOnly = true)
    public List<PtmBooking> bookingsForStudent(UUID tenantId, UUID studentId) {
        studentAccessGuard.assertInTenant(tenantId, studentId);
        return bookingRepo.findByStudentIdOrderByCreatedAtDesc(studentId);
    }
}
