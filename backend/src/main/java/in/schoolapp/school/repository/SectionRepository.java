package in.schoolapp.school.repository;

import in.schoolapp.school.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SectionRepository extends JpaRepository<Section, UUID> {

    List<Section> findBySchoolIdAndAcademicYearId(UUID schoolId, UUID academicYearId);

    Optional<Section> findBySchoolIdAndClassIdAndAcademicYearIdAndName(
        UUID schoolId, UUID classId, UUID academicYearId, String name);

    List<Section> findByClassIdAndAcademicYearIdOrderByName(UUID classId, UUID academicYearId);

    /** Returns all sections in a school that have the given staff member set as class teacher. */
    List<Section> findByClassTeacherIdAndSchoolId(UUID classTeacherId, UUID schoolId);

    Optional<Section> findByIdAndSchoolId(UUID id, UUID schoolId);

    /**
     * Sections in the current academic year that have no {@code attendance_records} row for
     * the given date — feeds {@link in.schoolapp.analytics.detector.AttendanceNotSubmittedDetector}
     * and the principal's "unmarked sections" dashboard tile.
     */
    @Query(value = """
        SELECT s.*
        FROM sections s
        JOIN academic_years ay ON ay.id = s.academic_year_id
        WHERE s.school_id = :schoolId
          AND ay.is_current = TRUE
          AND NOT EXISTS (
              SELECT 1 FROM attendance_records ar
              WHERE ar.section_id = s.id AND ar.date = :date
          )
        ORDER BY s.name
        """, nativeQuery = true)
    List<Section> findUnmarkedSectionsForDate(
        @Param("schoolId") UUID schoolId,
        @Param("date") LocalDate date);
}
