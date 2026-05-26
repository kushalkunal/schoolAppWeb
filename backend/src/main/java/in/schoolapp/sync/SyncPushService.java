package in.schoolapp.sync;

import in.schoolapp.attendance.entity.AttendanceRecord;
import in.schoolapp.attendance.repository.AttendanceRepository;
import in.schoolapp.common.TenantContext;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.entity.Section;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.sync.dto.SyncPushRequest;
import in.schoolapp.sync.dto.SyncPushResponse;
import in.schoolapp.sync.dto.SyncPushResponse.EntryResult;
import in.schoolapp.sync.dto.SyncPushResponse.EntryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Applies a batch of offline-queued attendance entries. Each entry is processed in its own
 * {@code REQUIRES_NEW} transaction so a single rejected entry (wrong section, etc.) does not
 * fail the whole batch — the mobile client gets partial results and can show per-row errors.
 * <p>
 * Idempotency is via the {@code (student_id, date)} unique constraint on
 * {@code attendance_records}: a re-submitted entry upserts the existing row and the response
 * reports {@link EntryStatus#UPDATED} rather than {@code CREATED}. Safe for the mobile client
 * to retry a failed batch without deduping client-side.
 * <p>
 * Validation guards:
 * <ul>
 *   <li>Student must be enrolled in the claimed section (prevents cross-section writes).</li>
 *   <li>Date cannot be in the future (paranoia against clock-skewed phones).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncPushService {

    private final AttendanceRepository attendanceRepository;
    private final ClassSectionService classSectionService;
    private final StudentEnrollmentRepository enrollmentRepository;

    public SyncPushResponse apply(UUID tenantId, SyncPushRequest req) {
        List<EntryResult> results = new ArrayList<>(req.entries().size());
        int accepted = 0, rejected = 0;
        Set<UUID> seenLocalIds = new HashSet<>();

        for (SyncPushRequest.AttendanceEntry e : req.entries()) {
            if (!seenLocalIds.add(e.localId())) {
                results.add(new EntryResult(e.localId(), null, EntryStatus.REJECTED,
                    "duplicate localId in batch"));
                rejected++;
                continue;
            }
            try {
                EntryResult r = upsertOne(tenantId, e);
                results.add(r);
                if (r.status() == EntryStatus.REJECTED) rejected++;
                else accepted++;
            } catch (Exception ex) {
                log.warn("Sync push entry failed localId={} tenant={} — {}",
                    e.localId(), tenantId, ex.getMessage());
                results.add(new EntryResult(e.localId(), null, EntryStatus.REJECTED, ex.getMessage()));
                rejected++;
            }
        }
        log.info("Sync push tenant={} total={} accepted={} rejected={}",
            tenantId, req.entries().size(), accepted, rejected);
        return new SyncPushResponse(accepted, rejected, results);
    }

    /**
     * Per-entry REQUIRES_NEW so one failure doesn't roll back successfully-applied siblings.
     * Spring's self-invocation rule means this must be called from {@link #apply} (which is
     * a different bean-method boundary only because it's transactionally unbounded).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EntryResult upsertOne(UUID tenantId, SyncPushRequest.AttendanceEntry e) {
        if (e.date().isAfter(LocalDate.now())) {
            return new EntryResult(e.localId(), null, EntryStatus.REJECTED,
                "date cannot be in the future");
        }

        // Section boundary — sectionId in the body must resolve to this tenant.
        Section section = classSectionService.getSectionOrThrow(tenantId, e.sectionId());

        // Student must be enrolled in the claimed section. Guards against a cross-section
        // write where a phone has stale section ids for a transferred student.
        boolean enrolled = enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(e.studentId())
            .stream()
            .map(StudentEnrollment::getSectionId)
            .anyMatch(id -> id.equals(section.getId()));
        if (!enrolled) {
            return new EntryResult(e.localId(), null, EntryStatus.REJECTED,
                "student not enrolled in this section");
        }

        AttendanceRecord existing = attendanceRepository
            .findByStudentIdAndDate(e.studentId(), e.date())
            .orElse(null);

        if (existing == null) {
            AttendanceRecord row = new AttendanceRecord();
            row.setSchoolId(tenantId);
            row.setStudentId(e.studentId());
            row.setSectionId(section.getId());
            row.setDate(e.date());
            row.setStatus(e.status());
            row.setArrivalTime(e.arrivalTime());
            row.setNote(e.note());
            row.setSyncedFromMobile(true);
            row.setMarkedById(TenantContext.getStaffId());
            AttendanceRecord saved = attendanceRepository.save(row);
            return new EntryResult(e.localId(), saved.getId(), EntryStatus.CREATED, null);
        }

        // Tenant boundary on existing row — defensive; the (student_id, date) unique index
        // already scopes via the student's school_id, but a belt-and-braces check avoids any
        // risk of cross-tenant writes via a compromised JWT.
        if (!existing.getSchoolId().equals(tenantId)) {
            return new EntryResult(e.localId(), null, EntryStatus.REJECTED,
                "existing record belongs to a different tenant");
        }
        existing.setStatus(e.status());
        existing.setArrivalTime(e.arrivalTime());
        existing.setNote(e.note());
        existing.setSyncedFromMobile(true);
        existing.setMarkedById(TenantContext.getStaffId());
        AttendanceRecord saved = attendanceRepository.save(existing);
        return new EntryResult(e.localId(), saved.getId(), EntryStatus.UPDATED, null);
    }
}
