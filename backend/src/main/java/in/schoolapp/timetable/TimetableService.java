package in.schoolapp.timetable;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.timetable.dto.PeriodDto;
import in.schoolapp.timetable.dto.TimetableEntryDto;
import in.schoolapp.timetable.entity.TimetableEntry;
import in.schoolapp.timetable.entity.TimetablePeriod;
import in.schoolapp.timetable.repository.TimetableEntryRepository;
import in.schoolapp.timetable.repository.TimetablePeriodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimetableService {

    private final TimetablePeriodRepository periodRepository;
    private final TimetableEntryRepository entryRepository;

    // ---------- Periods ----------

    @Transactional(readOnly = true)
    public List<PeriodDto> listPeriods(UUID tenantId) {
        return periodRepository.findBySchoolIdOrderBySortOrderAsc(tenantId).stream()
            .map(PeriodDto::from).toList();
    }

    @Transactional
    public PeriodDto createPeriod(UUID tenantId, PeriodDto req) {
        if (!req.endTime().isAfter(req.startTime())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "endTime must be after startTime");
        }
        TimetablePeriod p = new TimetablePeriod();
        p.setSchoolId(tenantId);
        p.setName(req.name());
        p.setStartTime(req.startTime());
        p.setEndTime(req.endTime());
        p.setSortOrder(req.sortOrder());
        p.setBreakSlot(req.breakSlot());
        return PeriodDto.from(periodRepository.save(p));
    }

    @Transactional
    public void deletePeriod(UUID tenantId, UUID periodId) {
        TimetablePeriod p = periodRepository.findById(periodId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Period", periodId));
        if (!p.getSchoolId().equals(tenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Period not in this school");
        }
        periodRepository.delete(p);
    }

    // ---------- Entries ----------

    @Transactional(readOnly = true)
    public List<TimetableEntryDto> getSectionTimetable(UUID tenantId, UUID sectionId) {
        // Tenant scope check happens at the controller via TenantInterceptor; entries
        // inherit their section's school binding via BaseEntity.schoolId on insert.
        return entryRepository.findBySectionIdOrderByDayOfWeekAsc(sectionId).stream()
            .filter(e -> e.getSchoolId().equals(tenantId))
            .map(TimetableEntryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TimetableEntryDto> getTeacherTimetable(UUID tenantId, UUID teacherId) {
        return entryRepository.findByTeacherId(teacherId).stream()
            .filter(e -> e.getSchoolId().equals(tenantId))
            .map(TimetableEntryDto::from).toList();
    }

    @Transactional
    public TimetableEntryDto upsertEntry(UUID tenantId, TimetableEntryDto req) {
        TimetableEntry e;
        if (req.id() != null) {
            e = entryRepository.findById(req.id())
                .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "TimetableEntry", req.id()));
            if (!e.getSchoolId().equals(tenantId)) {
                throw new AppException(ErrorCode.FORBIDDEN, "Entry not in this school");
            }
        } else {
            e = new TimetableEntry();
            e.setSchoolId(tenantId);
            e.setSectionId(req.sectionId());
            e.setPeriodId(req.periodId());
            e.setDayOfWeek(req.dayOfWeek());
        }
        e.setSubjectId(req.subjectId());
        e.setTeacherId(req.teacherId());
        e.setNote(req.note());
        return TimetableEntryDto.from(entryRepository.save(e));
    }

    @Transactional
    public void deleteEntry(UUID tenantId, UUID entryId) {
        TimetableEntry e = entryRepository.findById(entryId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "TimetableEntry", entryId));
        if (!e.getSchoolId().equals(tenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Entry not in this school");
        }
        entryRepository.delete(e);
    }
}
