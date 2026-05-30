package in.schoolapp.school;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.dto.AssignClassTeacherRequest;
import in.schoolapp.school.dto.ClassResponse;
import in.schoolapp.school.dto.CreateClassesRequest;
import in.schoolapp.school.dto.SectionResponse;
import in.schoolapp.school.entity.AcademicYear;
import in.schoolapp.school.entity.SchoolClass;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.entity.Staff;
import in.schoolapp.school.entity.StaffRole;
import in.schoolapp.school.repository.SchoolClassRepository;
import in.schoolapp.school.repository.SectionRepository;
import in.schoolapp.school.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassSectionService {

    private final SchoolClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final AcademicYearService academicYearService;
    private final StaffRepository staffRepository;

    /**
     * Bulk-creates or updates class + section rows for the current academic year. Upserts rather
     * than inserts — if a class or section with the same name exists, it is reused so re-running
     * this endpoint is idempotent.
     */
    @Transactional
    public List<ClassResponse> bulkCreate(UUID schoolId, CreateClassesRequest req) {
        AcademicYear currentYear = academicYearService.getCurrentOrThrow(schoolId);
        List<ClassResponse> results = new ArrayList<>();

        for (int i = 0; i < req.classes().size(); i++) {
            CreateClassesRequest.ClassSpec spec = req.classes().get(i);
            String className = spec.name().trim();

            SchoolClass existing = classRepository.findBySchoolIdAndName(schoolId, className)
                .orElseGet(() -> {
                    SchoolClass c = new SchoolClass();
                    c.setSchoolId(schoolId);
                    c.setName(className);
                    return c;
                });
            existing.setSortOrder(spec.sortOrder() != null ? spec.sortOrder() : i);
            final SchoolClass cls = classRepository.save(existing);

            List<SectionResponse> sectionDtos = new ArrayList<>();
            for (String sectionName : spec.sections()) {
                final String trimmed = sectionName.trim();
                Section section = sectionRepository
                    .findBySchoolIdAndClassIdAndAcademicYearIdAndName(
                        schoolId, cls.getId(), currentYear.getId(), trimmed)
                    .orElseGet(() -> {
                        Section s = new Section();
                        s.setSchoolId(schoolId);
                        s.setClassId(cls.getId());
                        s.setAcademicYearId(currentYear.getId());
                        s.setName(trimmed);
                        return s;
                    });
                section = sectionRepository.save(section);
                sectionDtos.add(SectionResponse.from(section));
            }
            results.add(ClassResponse.from(cls, sectionDtos));
        }

        results.sort(Comparator.comparingInt(ClassResponse::sortOrder));
        return results;
    }

    @Transactional(readOnly = true)
    public List<ClassResponse> listClasses(UUID schoolId) {
        AcademicYear currentYear = academicYearService.getCurrentOrThrow(schoolId);
        List<SchoolClass> classes = classRepository.findBySchoolIdOrderBySortOrderAscNameAsc(schoolId);

        Map<UUID, List<SectionResponse>> sectionsByClass = sectionRepository
            .findBySchoolIdAndAcademicYearId(schoolId, currentYear.getId()).stream()
            .collect(Collectors.groupingBy(
                Section::getClassId,
                Collectors.mapping(SectionResponse::from, Collectors.toList())));

        return classes.stream()
            .map(c -> ClassResponse.from(c, sectionsByClass.getOrDefault(c.getId(), List.of())))
            .toList();
    }

    public Section getSectionOrThrow(UUID schoolId, UUID sectionId) {
        Section s = sectionRepository.findById(sectionId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.SECTION_NOT_FOUND, "Section", sectionId));
        if (!s.getSchoolId().equals(schoolId)) {
            throw AppException.notFound(ErrorCode.SECTION_NOT_FOUND, "Section", sectionId);
        }
        return s;
    }

    /**
     * Assigns a CLASS_TEACHER staff member as the class teacher of a section.
     * Only staff with the CLASS_TEACHER role may be assigned. Passing {@code null} as staffId
     * in the request is handled by the controller (validation rejects it), so this method
     * always receives a non-null staffId.
     *
     * <p><b>Uniqueness rule</b>: by default a teacher may only be the class teacher of ONE
     * section at a time. Re-assigning to the same section (idempotent) is always allowed.
     * Assigning the same teacher to a second section throws a {@code VALIDATION_ERROR} so the
     * admin must first remove them from their current class before reassigning.
     */
    @Transactional
    public SectionResponse assignClassTeacher(UUID schoolId, UUID sectionId, UUID staffId) {
        Section section = getSectionOrThrow(schoolId, sectionId);

        Staff staff = staffRepository.findByIdAndSchoolId(staffId, schoolId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.VALIDATION_ERROR, "Staff", staffId));
        if (staff.getRole() != StaffRole.CLASS_TEACHER) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Only a CLASS_TEACHER staff member can be assigned as class teacher. "
                    + staff.displayName() + " has role " + staff.getRole());
        }

        // Uniqueness: teacher can only be class teacher of one section at a time.
        // Allow re-assignment to the same section (idempotent).
        List<Section> alreadyAssigned = sectionRepository
            .findByClassTeacherIdAndSchoolId(staffId, schoolId);
        boolean alreadyThisSection = alreadyAssigned.stream()
            .anyMatch(s -> s.getId().equals(sectionId));
        if (!alreadyThisSection && !alreadyAssigned.isEmpty()) {
            Section existing = alreadyAssigned.get(0);
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                staff.displayName() + " is already the class teacher of " + existing.getName()
                    + ". Remove the existing assignment first, or contact an admin to allow"
                    + " multiple class-teacher assignments.");
        }

        section.setClassTeacherId(staffId);
        sectionRepository.save(section);
        return SectionResponse.from(section);
    }

    /**
     * Returns every section in this school where the given staff member is the class teacher.
     * Enriches each section with its class name for display.
     * Used by CLASS_TEACHER login to fetch only their assigned sections.
     */
    @Transactional(readOnly = true)
    public List<SectionResponse> getMyAssignedSections(UUID schoolId, UUID staffId) {
        List<Section> sections = sectionRepository.findByClassTeacherIdAndSchoolId(staffId, schoolId);
        Map<UUID, String> classNames = classRepository.findBySchoolIdOrderBySortOrderAscNameAsc(schoolId).stream()
            .collect(Collectors.toMap(SchoolClass::getId, SchoolClass::getName));
        return sections.stream()
            .map(s -> SectionResponse.from(s, classNames.getOrDefault(s.getClassId(), "")))
            .toList();
    }
}
