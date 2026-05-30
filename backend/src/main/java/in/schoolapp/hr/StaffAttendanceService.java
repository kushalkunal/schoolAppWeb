package in.schoolapp.hr;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.hr.dto.StaffAttendanceRequest;
import in.schoolapp.hr.dto.StaffAttendanceResponse;
import in.schoolapp.hr.dto.StaffMonthlySummaryResponse;
import in.schoolapp.hr.entity.StaffAttendance;
import in.schoolapp.hr.entity.StaffAttendance.StaffAttendanceStatus;
import in.schoolapp.hr.repository.StaffAttendanceRepository;
import in.schoolapp.school.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff self-attendance with principal/admin approval.
 * Teachers mark their own daily attendance; principal/admin approves.
 * Upserts on (school, staff, date) so re-submitting the same day updates the existing row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffAttendanceService {

    private final StaffAttendanceRepository repository;
    private final StaffRepository staffRepository;

    private String staffName(UUID staffId) {
        return staffRepository.findById(staffId)
            .map(s -> s.getFirstName() + (s.getLastName() != null ? " " + s.getLastName() : ""))
            .orElse("Unknown");
    }

    private StaffAttendanceResponse enrich(StaffAttendance row) {
        String staffName = staffName(row.getStaffId());
        String approvedByName = row.getApprovedById() == null ? null : staffName(row.getApprovedById());
        return new StaffAttendanceResponse(
            row.getId(), row.getStaffId(), staffName, row.getAttendanceDate(),
            row.getStatus(), row.getNotes(),
            row.isApproved(), row.getApprovedById(), approvedByName, row.getApprovedAt());
    }

    /**
     * Teacher marks their own attendance. The staffId in the request must match the
     * caller's own staffId (enforced in the controller via @PreAuthorize or explicit check).
     * Sets approved = false so the principal/admin can review.
     */
    @Transactional
    public StaffAttendanceResponse markSelf(UUID tenantId, UUID staffId,
                                            StaffAttendanceStatus status, String notes) {
        StaffAttendance row = repository
            .findBySchoolIdAndStaffIdAndAttendanceDate(tenantId, staffId, LocalDate.now())
            .orElseGet(() -> {
                StaffAttendance fresh = new StaffAttendance();
                fresh.setSchoolId(tenantId);
                fresh.setStaffId(staffId);
                fresh.setAttendanceDate(LocalDate.now());
                return fresh;
            });
        row.setStatus(status);
        row.setNotes(notes);
        row.setMarkedById(staffId);
        row.setApproved(false);
        row.setApprovedById(null);
        row.setApprovedAt(null);
        row = repository.save(row);
        log.info("Staff self-attendance marked tenantId={} staffId={} status={}", tenantId, staffId, status);
        return enrich(row);
    }

    /** Returns the requesting teacher's own attendance history. */
    @Transactional(readOnly = true)
    public List<StaffAttendanceResponse> getMyAttendance(UUID tenantId, UUID staffId,
                                                          LocalDate from, LocalDate to) {
        return repository.findStaffRange(tenantId, staffId, from, to)
            .stream().map(this::enrich).toList();
    }

    /** Admin: list all pending-approval entries for a given date. */
    @Transactional(readOnly = true)
    public List<StaffAttendanceResponse> pendingApprovals(UUID tenantId, LocalDate date) {
        return repository.findBySchoolIdAndAttendanceDateAndApproved(tenantId, date, false)
            .stream().map(this::enrich).toList();
    }

    /** Admin: approve a teacher's self-attendance for today or a specific date. */
    @Transactional
    public StaffAttendanceResponse approve(UUID tenantId, UUID staffId, LocalDate date) {
        StaffAttendance row = repository
            .findBySchoolIdAndStaffIdAndAttendanceDate(tenantId, staffId, date)
            .orElseThrow(() -> new AppException(ErrorCode.ATTENDANCE_RECORD_NOT_FOUND,
                "No staff attendance found for staffId=" + staffId + " on " + date));
        UUID approverId = TenantContext.getStaffId();
        row.setApproved(true);
        row.setApprovedById(approverId);
        row.setApprovedAt(OffsetDateTime.now());
        row = repository.save(row);
        log.info("Staff attendance approved tenantId={} staffId={} date={} by={}", tenantId, staffId, date, approverId);
        return enrich(row);
    }

    // ---- Legacy bulk-mark (admin marks multiple staff at once, auto-approved) ----

    @Transactional
    public List<StaffAttendanceResponse> bulkMark(UUID tenantId, List<StaffAttendanceRequest> requests) {
        return requests.stream().map(req -> mark(tenantId, req)).toList();
    }

    @Transactional
    public StaffAttendanceResponse mark(UUID tenantId, StaffAttendanceRequest req) {
        StaffAttendance row = repository
            .findBySchoolIdAndStaffIdAndAttendanceDate(tenantId, req.staffId(), req.date())
            .orElseGet(() -> {
                StaffAttendance fresh = new StaffAttendance();
                fresh.setSchoolId(tenantId);
                fresh.setStaffId(req.staffId());
                fresh.setAttendanceDate(req.date());
                return fresh;
            });
        row.setStatus(req.status());
        row.setNotes(req.notes());
        // Admin bulk-mark is auto-approved
        row.setApproved(true);
        row = repository.save(row);
        return enrich(row);
    }

    public List<StaffAttendanceResponse> listForDate(UUID tenantId, LocalDate date) {
        return repository.findBySchoolIdAndAttendanceDate(tenantId, date)
            .stream().map(this::enrich).toList();
    }

    public StaffMonthlySummaryResponse monthlySummary(UUID tenantId, UUID staffId, int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        var rows = repository.findStaffRange(tenantId, staffId, from, to);

        Map<StaffAttendanceStatus, Integer> counts = new EnumMap<>(StaffAttendanceStatus.class);
        for (StaffAttendanceStatus s : StaffAttendanceStatus.values()) counts.put(s, 0);
        for (var r : rows) counts.merge(r.getStatus(), 1, Integer::sum);

        BigDecimal workingDays = BigDecimal.valueOf(counts.get(StaffAttendanceStatus.PRESENT))
            .add(BigDecimal.valueOf(counts.get(StaffAttendanceStatus.LATE)))
            .add(new BigDecimal("0.5").multiply(BigDecimal.valueOf(counts.get(StaffAttendanceStatus.HALF_DAY))));

        return new StaffMonthlySummaryResponse(staffId, year, month, from.lengthOfMonth(),
            counts, workingDays);
    }
}
