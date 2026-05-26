package in.schoolapp.academics;

import in.schoolapp.academics.dto.CreateTeacherAssignmentRequest;
import in.schoolapp.academics.dto.TeacherAssignmentResponse;
import in.schoolapp.academics.entity.TeacherSubjectAssignment;
import in.schoolapp.academics.repository.TeacherSubjectAssignmentRepository;
import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.AcademicYearService;
import in.schoolapp.school.entity.AcademicYear;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeacherAssignmentService {

    private final TeacherSubjectAssignmentRepository repository;
    private final AcademicYearService academicYearService;
    private final AuditLogger auditLogger;

    @Transactional
    public TeacherAssignmentResponse assign(UUID tenantId, CreateTeacherAssignmentRequest req) {
        AcademicYear year = academicYearService.getCurrentOrThrow(tenantId);
        if (repository.existsByStaffIdAndSubjectIdAndSectionIdAndAcademicYearId(
                req.staffId(), req.subjectId(), req.sectionId(), year.getId())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "This assignment already exists for the current academic year");
        }
        TeacherSubjectAssignment a = new TeacherSubjectAssignment();
        a.setSchoolId(tenantId);
        a.setStaffId(req.staffId());
        a.setSubjectId(req.subjectId());
        a.setSectionId(req.sectionId());
        a.setAcademicYearId(year.getId());
        a = repository.save(a);
        auditLogger.logCreate(tenantId, "TeacherSubjectAssignment", a.getId(), Map.of(
            "staffId", a.getStaffId(),
            "subjectId", a.getSubjectId(),
            "sectionId", a.getSectionId()
        ));
        return TeacherAssignmentResponse.from(a);
    }

    @Transactional(readOnly = true)
    public List<TeacherAssignmentResponse> listCurrent(UUID tenantId) {
        AcademicYear year = academicYearService.getCurrentOrThrow(tenantId);
        return repository.findBySchoolIdAndAcademicYearId(tenantId, year.getId()).stream()
            .map(TeacherAssignmentResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TeacherAssignmentResponse> listForStaff(UUID tenantId, UUID staffId) {
        AcademicYear year = academicYearService.getCurrentOrThrow(tenantId);
        return repository.findByStaffIdAndAcademicYearId(staffId, year.getId()).stream()
            .filter(a -> a.getSchoolId().equals(tenantId))
            .map(TeacherAssignmentResponse::from)
            .toList();
    }

    @Transactional
    public void unassign(UUID tenantId, UUID assignmentId) {
        TeacherSubjectAssignment a = repository.findByIdAndSchoolId(assignmentId, tenantId)
            .orElseThrow(() -> AppException.notFound(
                ErrorCode.RESOURCE_NOT_FOUND, "TeacherSubjectAssignment", assignmentId));
        repository.delete(a);
        auditLogger.logDelete(tenantId, "TeacherSubjectAssignment", assignmentId, Map.of(
            "staffId", a.getStaffId(),
            "subjectId", a.getSubjectId(),
            "sectionId", a.getSectionId()
        ));
    }
}
