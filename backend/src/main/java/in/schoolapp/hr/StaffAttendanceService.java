package in.schoolapp.hr;

import in.schoolapp.hr.dto.StaffAttendanceRequest;
import in.schoolapp.hr.dto.StaffAttendanceResponse;
import in.schoolapp.hr.dto.StaffMonthlySummaryResponse;
import in.schoolapp.hr.entity.StaffAttendance;
import in.schoolapp.hr.entity.StaffAttendance.StaffAttendanceStatus;
import in.schoolapp.hr.repository.StaffAttendanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Marks staff attendance. Upserts on (school, staff, date) so re-submitting the same day
 * updates the existing row rather than throwing a uniqueness violation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffAttendanceService {

    private final StaffAttendanceRepository repository;

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
        row = repository.save(row);
        return StaffAttendanceResponse.from(row);
    }

    public List<StaffAttendanceResponse> listForDate(UUID tenantId, LocalDate date) {
        return repository.findBySchoolIdAndAttendanceDate(tenantId, date)
            .stream().map(StaffAttendanceResponse::from).toList();
    }

    /**
     * Monthly summary for one staff member — counts by status. Used by the payroll
     * calculator and the staff profile page.
     */
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
