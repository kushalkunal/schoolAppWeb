package in.schoolapp.academics;

import in.schoolapp.academics.entity.Exam;
import in.schoolapp.academics.entity.ExamResult;
import in.schoolapp.academics.entity.ResultStatus;
import in.schoolapp.academics.repository.ExamMarkRepository;
import in.schoolapp.academics.repository.ExamResultRepository;
import in.schoolapp.academics.repository.ExamSubjectConfigRepository;
import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.SchoolService;
import in.schoolapp.school.entity.Section;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Result class-teacher verification gate (audit #7): verify is class-teacher-scoped and publish
 *  requires a prior verification. */
@ExtendWith(MockitoExtension.class)
class ResultServiceTest {

    @Mock ExamResultRepository resultRepository;
    @Mock ExamMarkRepository markRepository;
    @Mock ExamSubjectConfigRepository configRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock ExamService examService;
    @Mock ClassSectionService classSectionService;
    @Mock StudentRepository studentRepository;
    @Mock StudentEnrollmentRepository enrollmentRepository;
    @Mock SchoolService schoolService;
    @Mock GradeCalculator gradeCalculator;
    @Mock ReportCardService reportCardService;
    @Mock AuditLogger auditLogger;
    @InjectMocks ResultService service;

    final UUID tenant = UUID.randomUUID();
    final UUID examId = UUID.randomUUID();
    final UUID sectionId = UUID.randomUUID();
    final UUID classTeacher = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Section section(UUID classTeacherId) {
        Section s = new Section();
        s.setSchoolId(tenant);
        s.setClassTeacherId(classTeacherId);
        return s;
    }

    private void stubEmptyReadBack() {
        lenient().when(resultRepository.findByExamIdAndSectionIdOrderByRankInSectionAsc(examId, sectionId))
            .thenReturn(List.of());
        lenient().when(studentRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(enrollmentRepository.findBySectionIdAndStatus(any(), any())).thenReturn(List.of());
    }

    @Test
    void classTeacherVerifiesReadyResults() {
        TenantContext.set(tenant, classTeacher, "CLASS_TEACHER");
        Exam exam = new Exam();
        when(examService.getExamOrThrow(tenant, examId)).thenReturn(exam);
        when(classSectionService.getSectionOrThrow(tenant, sectionId)).thenReturn(section(classTeacher));
        ExamResult r = new ExamResult();
        r.setStatus(ResultStatus.READY);
        when(resultRepository.findByExamIdAndSectionIdAndStatus(examId, sectionId, ResultStatus.READY))
            .thenReturn(List.of(r));
        when(resultRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        stubEmptyReadBack();

        service.verifySection(tenant, examId, sectionId);

        assertThat(r.getStatus()).isEqualTo(ResultStatus.VERIFIED);
        assertThat(exam.getResultStatus()).isEqualTo(ResultStatus.VERIFIED);
        verify(auditLogger).logAction(eq(tenant), eq("ExamResult"), eq(examId), eq("VERIFY_RESULTS"), any());
    }

    @Test
    void subjectTeacherCannotVerify() {
        TenantContext.set(tenant, UUID.randomUUID(), "SUBJECT_TEACHER");   // not the class teacher
        when(examService.getExamOrThrow(tenant, examId)).thenReturn(new Exam());
        when(classSectionService.getSectionOrThrow(tenant, sectionId)).thenReturn(section(classTeacher));

        assertThatThrownBy(() -> service.verifySection(tenant, examId, sectionId))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void publishRejectedWhenNotVerified() {
        when(examService.getExamOrThrow(tenant, examId)).thenReturn(new Exam());
        when(classSectionService.getSectionOrThrow(tenant, sectionId)).thenReturn(section(classTeacher));
        when(resultRepository.findByExamIdAndSectionIdAndStatus(examId, sectionId, ResultStatus.VERIFIED))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.publishSection(tenant, examId, sectionId))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
