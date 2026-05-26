package in.schoolapp.hr.repository;

import in.schoolapp.hr.entity.StaffAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffAttendanceRepository extends JpaRepository<StaffAttendance, UUID> {

    Optional<StaffAttendance> findBySchoolIdAndStaffIdAndAttendanceDate(
        UUID schoolId, UUID staffId, LocalDate date);

    List<StaffAttendance> findBySchoolIdAndAttendanceDate(UUID schoolId, LocalDate date);

    @Query("""
        SELECT a FROM StaffAttendance a
        WHERE a.schoolId = :schoolId
          AND a.staffId = :staffId
          AND a.attendanceDate BETWEEN :from AND :to
        ORDER BY a.attendanceDate
        """)
    List<StaffAttendance> findStaffRange(@Param("schoolId") UUID schoolId,
                                         @Param("staffId") UUID staffId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);
}
