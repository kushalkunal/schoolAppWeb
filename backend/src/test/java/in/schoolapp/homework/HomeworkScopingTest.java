package in.schoolapp.homework;

import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.academics.repository.TeacherSubjectAssignmentRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.homework.dto.AssignmentDto;
import in.schoolapp.homework.repository.HomeworkAssignmentRepository;
import in.schoolapp.homework.repository.HomeworkSubmissionRepository;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.StaffRepository;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.timetable.entity.TimetableSubstitution;
import in.schoolapp.timetable.repository.TimetableEntryRepository;
import in.schoolapp.timetable.repository.TimetableSubstitutionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Homework can only be assigned to a section the teacher actually teaches (regular or substitute). */
@ExtendWith(MockitoExtension.class)
class HomeworkScopingTest {

    @Mock HomeworkAssignmentRepository assignmentRepo;
    @Mock HomeworkSubmissionRepository submissionRepo;
    @Mock StudentEnrollmentRepository enrollmentRepo;
    @Mock in.schoolapp.communication.parent.ParentNotificationService parentNotificationService;
    @Mock ClassSectionService classSectionService;
    @Mock TeacherSubjectAssignmentRepository teacherAssignmentRepository;
    @Mock TimetableEntryRepository timetableEntryRepository;
    @Mock TimetableSubstitutionRepository substitutionRepository;
    @Mock StaffRepository staffRepository;
    @Mock SubjectRepository subjectRepository;
    @InjectMocks HomeworkService service;

    final UUID tenant = UUID.randomUUID();
    final UUID sectionId = UUID.randomUUID();
    final UUID yearId = UUID.randomUUID();
    final UUID teacher = UUID.randomUUID();

    @AfterEach void clear() { TenantContext.clear(); }

    private Section section(UUID classTeacherId) {
        Section s = new Section();
        s.setId(sectionId); s.setSchoolId(tenant); s.setAcademicYearId(yearId); s.setClassTeacherId(classTeacherId);
        return s;
    }
    private AssignmentDto req() {
        return new AssignmentDto(null, sectionId, null, "Read ch. 3", "pages 10-14", null,
            LocalDate.now().plusDays(2), null, null);
    }

    @Test
    void teacherNotTeachingSection_isForbidden() {
        TenantContext.set(tenant, teacher, "SUBJECT_TEACHER");
        when(classSectionService.getSectionOrThrow(tenant, sectionId)).thenReturn(section(UUID.randomUUID()));
        when(teacherAssignmentRepository.findByStaffIdAndAcademicYearId(teacher, yearId)).thenReturn(List.of());
        when(timetableEntryRepository.findByTeacherId(teacher)).thenReturn(List.of());
        when(substitutionRepository.findBySubstituteTeacherIdAndDate(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.createAssignment(tenant, req()))
            .isInstanceOf(AppException.class)
            .matches(e -> ((AppException) e).getErrorCode() == ErrorCode.FORBIDDEN);
    }

    @Test
    void classTeacherOfSection_canAssign() {
        TenantContext.set(tenant, teacher, "CLASS_TEACHER");
        when(classSectionService.getSectionOrThrow(tenant, sectionId)).thenReturn(section(teacher));  // CT == teacher
        when(assignmentRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatCode(() -> service.createAssignment(tenant, req())).doesNotThrowAnyException();
    }

    @Test
    void substituteForSectionToday_canAssign() {
        TenantContext.set(tenant, teacher, "SUBJECT_TEACHER");
        when(classSectionService.getSectionOrThrow(tenant, sectionId)).thenReturn(section(UUID.randomUUID()));
        lenient().when(teacherAssignmentRepository.findByStaffIdAndAcademicYearId(teacher, yearId)).thenReturn(List.of());
        lenient().when(timetableEntryRepository.findByTeacherId(teacher)).thenReturn(List.of());
        TimetableSubstitution sub = new TimetableSubstitution();
        sub.setSchoolId(tenant); sub.setSectionId(sectionId); sub.setSubstituteTeacherId(teacher);
        when(substitutionRepository.findBySubstituteTeacherIdAndDate(teacher, LocalDate.now())).thenReturn(List.of(sub));
        when(assignmentRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatCode(() -> service.createAssignment(tenant, req())).doesNotThrowAnyException();
    }
}
