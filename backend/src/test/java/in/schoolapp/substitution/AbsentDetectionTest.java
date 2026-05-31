package in.schoolapp.substitution;

import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.academics.repository.TeacherSubjectAssignmentRepository;
import in.schoolapp.hr.entity.LeaveApplication;
import in.schoolapp.hr.entity.LeaveApplication.LeaveStatus;
import in.schoolapp.hr.entity.LeaveApplication.LeaveType;
import in.schoolapp.hr.entity.StaffAttendance;
import in.schoolapp.hr.entity.StaffAttendance.StaffAttendanceStatus;
import in.schoolapp.hr.repository.LeaveApplicationRepository;
import in.schoolapp.hr.repository.StaffAttendanceRepository;
import in.schoolapp.school.AcademicYearService;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;
import in.schoolapp.school.repository.StaffRepository;
import in.schoolapp.substitution.dto.SubstitutionDtos.AbsentTeacher;
import in.schoolapp.timetable.TimetableService;
import in.schoolapp.timetable.repository.TimetableEntryRepository;
import in.schoolapp.timetable.repository.TimetablePeriodRepository;
import in.schoolapp.timetable.repository.TimetableSubstitutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Absent-teacher detection merges self-attendance (ABSENT/LEAVE) with approved leave covering today. */
@ExtendWith(MockitoExtension.class)
class AbsentDetectionTest {

    @Mock TimetableEntryRepository entryRepository;
    @Mock TimetablePeriodRepository periodRepository;
    @Mock TimetableSubstitutionRepository substitutionRepository;
    @Mock StaffRepository staffRepository;
    @Mock StaffAttendanceRepository staffAttendanceRepository;
    @Mock LeaveApplicationRepository leaveRepository;
    @Mock TeacherSubjectAssignmentRepository assignmentRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock AcademicYearService academicYearService;
    @Mock ClassSectionService classSectionService;
    @Mock TimetableService timetableService;
    @InjectMocks SubstitutionPlannerService planner;

    final UUID tenant = UUID.randomUUID();
    final LocalDate today = LocalDate.of(2026, 6, 1);

    private Staff teacher(UUID id, String name) {
        Staff s = new Staff();
        s.setId(id); s.setSchoolId(tenant); s.setFirstName(name); s.setRole(StaffRole.SUBJECT_TEACHER); s.setActive(true);
        return s;
    }

    @Test
    void mergesAttendanceAbsentAndApprovedLeave() {
        UUID absentByAttendance = UUID.randomUUID();
        UUID onLeave = UUID.randomUUID();
        UUID present = UUID.randomUUID();

        when(staffRepository.findBySchoolIdAndActiveTrueOrderByFirstName(tenant))
            .thenReturn(List.of(teacher(absentByAttendance, "Asha"), teacher(onLeave, "Bina"), teacher(present, "Chetan")));

        StaffAttendance att = new StaffAttendance();
        att.setStaffId(absentByAttendance);
        att.setStatus(StaffAttendanceStatus.ABSENT);
        when(staffAttendanceRepository.findBySchoolIdAndAttendanceDate(tenant, today)).thenReturn(List.of(att));

        LeaveApplication leave = new LeaveApplication();
        leave.setStaffId(onLeave);
        leave.setStatus(LeaveStatus.APPROVED);
        leave.setLeaveType(LeaveType.CASUAL);
        leave.setStartDate(today.minusDays(1));
        leave.setEndDate(today.plusDays(1));
        leave.setDays(BigDecimal.ONE);
        when(leaveRepository.findBySchoolIdAndStatusOrderByCreatedAtDesc(tenant, LeaveStatus.APPROVED))
            .thenReturn(List.of(leave));

        List<AbsentTeacher> absent = planner.absentTeachersToday(tenant, today);

        assertThat(absent).extracting(AbsentTeacher::staffId)
            .containsExactlyInAnyOrder(absentByAttendance, onLeave);   // present teacher excluded
        assertThat(absent).noneMatch(a -> a.staffId().equals(present));
        assertThat(absent).anyMatch(a -> a.staffId().equals(onLeave) && a.reason().contains("leave"));
    }
}
