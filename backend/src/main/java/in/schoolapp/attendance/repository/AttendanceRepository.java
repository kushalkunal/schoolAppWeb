package in.schoolapp.attendance.repository;

import in.schoolapp.attendance.entity.AttendanceRecord;
import in.schoolapp.attendance.entity.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, UUID> {

    Optional<AttendanceRecord> findByStudentIdAndDate(UUID studentId, LocalDate date);

    List<AttendanceRecord> findBySchoolIdAndSectionIdAndDate(UUID schoolId, UUID sectionId, LocalDate date);

    /** Date-range export iterator — results ordered by date so the XLSX is easy to read. */
    Page<AttendanceRecord> findBySchoolIdAndDateBetweenOrderByDateAscStudentIdAsc(
        UUID schoolId, LocalDate from, LocalDate to, Pageable pageable);

    List<AttendanceRecord> findByStudentIdAndDateBetweenOrderByDateDesc(
        UUID studentId, LocalDate from, LocalDate to);

    long countBySchoolIdAndDateAndStatus(UUID schoolId, LocalDate date, AttendanceStatus status);

    long countBySchoolIdAndSectionIdAndDate(UUID schoolId, UUID sectionId, LocalDate date);

    boolean existsBySchoolIdAndSectionIdAndDate(UUID schoolId, UUID sectionId, LocalDate date);

    /**
     * Students with at least {@code threshold} absences in the window — the signal used by
     * {@link in.schoolapp.analytics.detector.ConsecutiveAbsenceDetector}. Rather than a true
     * consecutive-run SQL (requires window functions the multi-tenant query optimiser can't
     * help with), we use "absences in the last N school days" which is equivalent in practice
     * for the typical 3-day window — a student absent on all 3 recent marked days.
     */
    @Query(value = """
        SELECT student_id
        FROM attendance_records
        WHERE school_id = :schoolId
          AND date BETWEEN :fromDate AND :toDate
          AND status = 'ABSENT'
        GROUP BY student_id
        HAVING COUNT(*) >= :threshold
        """, nativeQuery = true)
    List<UUID> findStudentsWithAbsencesInWindow(
        @Param("schoolId") UUID schoolId,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate,
        @Param("threshold") int threshold);

    /**
     * Per-student absence counts over a longer window — used for the chronic-absentees
     * endpoint (principal view, typically 30 days). Returns {@code [studentId, absentCount]}
     * tuples descending by count.
     */
    @Query(value = """
        SELECT student_id AS studentId, COUNT(*) AS absentCount
        FROM attendance_records
        WHERE school_id = :schoolId
          AND date BETWEEN :fromDate AND :toDate
          AND status = 'ABSENT'
        GROUP BY student_id
        HAVING COUNT(*) >= :minAbsences
        ORDER BY COUNT(*) DESC
        """, nativeQuery = true)
    List<ChronicAbsenteeRow> findChronicAbsentees(
        @Param("schoolId") UUID schoolId,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate,
        @Param("minAbsences") int minAbsences);

    /** Projection row for {@link #findChronicAbsentees}. */
    interface ChronicAbsenteeRow {
        UUID getStudentId();
        long getAbsentCount();
    }

    long countBySchoolIdAndDate(UUID schoolId, LocalDate date);

    /**
     * Per-student (total, absent) counts used by at-risk scoring. Returns rows for every
     * student who has any attendance in the window; the detector computes the percentage
     * itself so different calling policies (school day calendar vs. raw count) can apply.
     */
    @Query(value = """
        SELECT student_id AS studentId,
               COUNT(*) AS totalCount,
               COUNT(*) FILTER (WHERE status = 'ABSENT') AS absentCount
        FROM attendance_records
        WHERE school_id = :schoolId
          AND date BETWEEN :fromDate AND :toDate
        GROUP BY student_id
        """, nativeQuery = true)
    List<StudentAttendanceCountRow> countPerStudentInWindow(
        @Param("schoolId") UUID schoolId,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate);

    interface StudentAttendanceCountRow {
        UUID getStudentId();
        long getTotalCount();
        long getAbsentCount();
    }
}
