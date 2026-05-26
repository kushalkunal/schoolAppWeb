package in.schoolapp.student;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.SchoolClassRepository;
import in.schoolapp.school.repository.SectionRepository;
import in.schoolapp.student.dto.FamilyViewResponse;
import in.schoolapp.student.dto.FamilyViewResponse.FamilyMember;
import in.schoolapp.student.dto.ParentDto;
import in.schoolapp.student.dto.StudentProfileResponse;
import in.schoolapp.student.dto.StudentProfileResponse.EnrollmentSummary;
import in.schoolapp.student.dto.StudentResponse;
import in.schoolapp.student.entity.Parent;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.entity.StudentEnrollment;
import in.schoolapp.student.entity.StudentParentLink;
import in.schoolapp.student.repository.ParentRepository;
import in.schoolapp.student.repository.StudentEnrollmentRepository;
import in.schoolapp.student.repository.StudentParentLinkRepository;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Assembles the "family" view across sibling detection, primary parent lookup, and joined
 * class/section names. Used by the student profile screen and (critically) by sibling-aware
 * absence/fee notifications — one combined WhatsApp instead of N per parent.
 */
@Service
@RequiredArgsConstructor
public class FamilyService {

    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final StudentParentLinkRepository linkRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final SectionRepository sectionRepository;
    private final SchoolClassRepository schoolClassRepository;

    /** Primary parent id for a student, if any. Null means no primary link configured. */
    @Transactional(readOnly = true)
    public UUID getPrimaryParentId(UUID studentId) {
        return linkRepository.findByStudentIdAndPrimaryTrue(studentId)
            .map(StudentParentLink::getParentId)
            .orElse(null);
    }

    /** All siblings (excluding the given student) that share any parent with the given student. */
    @Transactional(readOnly = true)
    public List<Student> findSiblings(UUID studentId) {
        Set<UUID> parentIds = linkRepository.findByStudentId(studentId).stream()
            .map(StudentParentLink::getParentId)
            .collect(Collectors.toSet());
        if (parentIds.isEmpty()) return List.of();

        Set<UUID> siblingIds = parentIds.stream()
            .flatMap(pid -> linkRepository.findByParentId(pid).stream())
            .map(StudentParentLink::getStudentId)
            .filter(id -> !id.equals(studentId))
            .collect(Collectors.toSet());
        return siblingIds.isEmpty() ? List.of() : studentRepository.findAllById(siblingIds);
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getProfile(UUID tenantId, UUID studentId) {
        Student student = studentRepository.findByIdAndSchoolId(studentId, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.STUDENT_NOT_FOUND, "Student", studentId));

        // Current enrollment (most recent by createdAt)
        EnrollmentSummary enrollment = enrollmentRepository
            .findByStudentIdOrderByCreatedAtDesc(studentId).stream()
            .findFirst()
            .map(this::toEnrollmentSummary)
            .orElse(null);

        // Parents (with their relation + isPrimary flag)
        List<StudentParentLink> links = linkRepository.findByStudentId(studentId);
        Map<UUID, Parent> parentsById = parentRepository
            .findAllById(links.stream().map(StudentParentLink::getParentId).toList())
            .stream().collect(Collectors.toMap(Parent::getId, p -> p));

        List<ParentDto> parentDtos = links.stream()
            .map(l -> ParentDto.from(parentsById.get(l.getParentId()), l.getRelation(), l.isPrimary()))
            .toList();

        List<StudentResponse> siblings = findSiblings(studentId).stream()
            .map(StudentResponse::from)
            .toList();

        return new StudentProfileResponse(
            StudentResponse.from(student),
            enrollment,
            parentDtos,
            siblings
        );
    }

    /**
     * Groups absent students by their primary parent for combined sibling-aware notifications
     * (gap analysis §5.6). Students without a primary parent are omitted — the caller decides
     * what to do with them (typically: alert the admin instead of a specific parent).
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<Student>> groupStudentsByPrimaryParent(List<Student> students) {
        Map<UUID, List<Student>> result = new java.util.HashMap<>();
        for (Student s : students) {
            UUID primaryParent = getPrimaryParentId(s.getId());
            if (primaryParent != null) {
                result.computeIfAbsent(primaryParent, k -> new ArrayList<>()).add(s);
            }
        }
        return result;
    }

    public Optional<Parent> getPrimaryParent(UUID studentId) {
        UUID parentId = getPrimaryParentId(studentId);
        return parentId == null ? Optional.empty() : parentRepository.findById(parentId);
    }

    @Transactional(readOnly = true)
    public FamilyViewResponse getFamilyByParent(UUID tenantId, UUID parentId) {
        Parent parent = parentRepository.findById(parentId)
            .filter(p -> p.getSchoolId().equals(tenantId))
            .orElseThrow(() -> AppException.notFound(ErrorCode.PARENT_NOT_FOUND, "Parent", parentId));

        List<UUID> studentIds = linkRepository.findByParentId(parentId).stream()
            .map(StudentParentLink::getStudentId)
            .toList();

        List<FamilyMember> members = new ArrayList<>();
        for (UUID studentId : studentIds) {
            Student s = studentRepository.findByIdAndSchoolId(studentId, tenantId).orElse(null);
            if (s == null || !s.isActive()) continue;

            enrollmentRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .findFirst()
                .ifPresent(enr -> {
                    Section section = sectionRepository.findById(enr.getSectionId()).orElse(null);
                    String className = section == null ? null
                        : schoolClassRepository.findById(section.getClassId())
                            .map(c -> c.getName()).orElse(null);
                    members.add(new FamilyMember(
                        s.getId(),
                        s.displayName(),
                        enr.getSectionId(),
                        className,
                        section == null ? null : section.getName()
                    ));
                });
        }

        return new FamilyViewResponse(
            parent.getId(),
            parent.getName(),
            parent.getPhone(),
            parent.getEmail(),
            members
        );
    }

    private EnrollmentSummary toEnrollmentSummary(StudentEnrollment enr) {
        Section section = sectionRepository.findById(enr.getSectionId()).orElse(null);
        String className = section == null ? null
            : schoolClassRepository.findById(section.getClassId()).map(c -> c.getName()).orElse(null);
        return new EnrollmentSummary(
            enr.getId(),
            enr.getAcademicYearId(),
            null,  // academicYearName — omitted; client rarely needs it
            enr.getSectionId(),
            className,
            section == null ? null : section.getName(),
            enr.getRollNumber(),
            enr.getStatus().name()
        );
    }
}
