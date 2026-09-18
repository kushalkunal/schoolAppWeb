package in.schoolapp.school;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.entity.AcademicYear;
import in.schoolapp.school.repository.AcademicYearRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AcademicYearService {

    private final AcademicYearRepository academicYearRepository;

    /**
     * Creates and returns the current Indian academic year for the given school. Indian schools
     * run April → March, so the "current" year is derived from today's month:
     * <ul>
     *   <li>If today is April or later, the year is April(thisYear) → March(nextYear).</li>
     *   <li>Otherwise (Jan–Mar), it is April(lastYear) → March(thisYear).</li>
     * </ul>
     */
    @Transactional
    public AcademicYear createCurrentYearForSchool(UUID schoolId) {
        LocalDate today = LocalDate.now();
        int startYear = today.getMonthValue() >= Month.APRIL.getValue() ? today.getYear() : today.getYear() - 1;
        LocalDate startDate = LocalDate.of(startYear, Month.APRIL, 1);
        LocalDate endDate = LocalDate.of(startYear + 1, Month.MARCH, 31);
        String name = startYear + "-" + (startYear + 1);

        if (academicYearRepository.existsBySchoolIdAndName(schoolId, name)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Academic year " + name + " already exists for this school");
        }

        AcademicYear year = new AcademicYear();
        year.setSchoolId(schoolId);
        year.setName(name);
        year.setStartDate(startDate);
        year.setEndDate(endDate);
        year.setCurrent(true);
        return academicYearRepository.save(year);
    }

    public AcademicYear getCurrentOrThrow(UUID schoolId) {
        return academicYearRepository.findBySchoolIdAndCurrentTrue(schoolId)
            .orElseThrow(() -> new AppException(ErrorCode.ACADEMIC_YEAR_NOT_FOUND,
                "No current academic year configured for school"));
    }

    /** Non-throwing variant — used by sync pull so a not-yet-onboarded tenant still gets
     *  a usable (empty) snapshot rather than a 404. */
    public Optional<AcademicYear> findCurrent(UUID schoolId) {
        return academicYearRepository.findBySchoolIdAndCurrentTrue(schoolId);
    }

    /** All academic sessions for the school, newest first — drives the session selectors. */
    public List<AcademicYear> listForSchool(UUID schoolId) {
        return academicYearRepository.findBySchoolIdOrderByStartDateDesc(schoolId);
    }

    /** Resolves a session id to the school's year, or falls back to the current year. */
    public AcademicYear resolveOrCurrent(UUID schoolId, UUID academicYearId) {
        if (academicYearId == null) return getCurrentOrThrow(schoolId);
        return academicYearRepository.findById(academicYearId)
            .filter(y -> schoolId.equals(y.getSchoolId()))
            .orElseThrow(() -> new AppException(ErrorCode.ACADEMIC_YEAR_NOT_FOUND,
                "Academic session not found for this school"));
    }
}
