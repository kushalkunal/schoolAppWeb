package in.schoolapp.school;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.school.dto.ClassResponse;
import in.schoolapp.school.dto.CreateClassesRequest;
import in.schoolapp.school.dto.SectionResponse;
import in.schoolapp.school.entity.AcademicYear;
import in.schoolapp.school.entity.SchoolClass;
import in.schoolapp.school.entity.Section;
import in.schoolapp.school.repository.SchoolClassRepository;
import in.schoolapp.school.repository.SectionRepository;
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
}
