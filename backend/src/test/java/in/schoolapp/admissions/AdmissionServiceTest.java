package in.schoolapp.admissions;

import in.schoolapp.admissions.dto.EnquiryRequest;
import in.schoolapp.admissions.entity.Admission;
import in.schoolapp.admissions.repository.AdmissionRepository;
import in.schoolapp.admissions.repository.AdmissionTestScoreRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.ClassSectionService;
import in.schoolapp.school.entity.School;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.SchoolRepository;
import in.schoolapp.student.StudentService;
import in.schoolapp.student.entity.EnrollmentStatus;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Admissions capacity + duplicate-applicant guards (audit #14). */
@ExtendWith(MockitoExtension.class)
class AdmissionServiceTest {

    @Mock AdmissionRepository admissionRepository;
    @Mock AdmissionTestScoreRepository scoreRepository;
    @Mock SchoolRepository schoolRepository;
    @Mock StudentService studentService;
    @Mock ClassSectionService classSectionService;
    @Mock StudentEnrollmentRepository enrollmentRepository;
    @InjectMocks AdmissionService service;

    final UUID tenant = UUID.randomUUID();
    final UUID sectionId = UUID.randomUUID();
    final UUID admissionId = UUID.randomUUID();

    private EnquiryRequest enquiry() {
        return new EnquiryRequest("Rajesh", "9876543210", null, "Aarav", null, null, null,
            "Grade 1", null, "2026-27", null, null, null);
    }

    @Test
    void duplicateActiveApplicationIsRejected() {
        School school = new School();
        school.setId(tenant);
        when(schoolRepository.findById(tenant)).thenReturn(Optional.of(school));
        when(admissionRepository.countActiveDuplicates(eq(tenant), eq("9876543210"), eq("Aarav"), anyList()))
            .thenReturn(1L);

        assertThatThrownBy(() -> service.createEnquiry(tenant, enquiry()))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_ADMISSION);

        verify(admissionRepository, never()).save(any());
    }

    @Test
    void enrollmentIntoFullSectionIsRejected() {
        when(admissionRepository.findByIdAndSchoolId(admissionId, tenant))
            .thenReturn(Optional.of(new Admission()));
        Section section = new Section();
        section.setSchoolId(tenant);
        section.setMaxStrength(2);
        when(classSectionService.getSectionOrThrow(tenant, sectionId)).thenReturn(section);
        when(enrollmentRepository.countBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE)).thenReturn(2L);

        assertThatThrownBy(() -> service.enrollStudent(tenant, admissionId, sectionId))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.SECTION_FULL);

        verify(studentService, never()).createStudent(any(), any());
    }
}
