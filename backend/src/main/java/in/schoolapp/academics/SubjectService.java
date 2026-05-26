package in.schoolapp.academics;

import in.schoolapp.academics.dto.CreateSubjectsRequest;
import in.schoolapp.academics.dto.SubjectResponse;
import in.schoolapp.academics.entity.Subject;
import in.schoolapp.academics.repository.SubjectRepository;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;

    /** Idempotent bulk create — existing names (case-sensitive) are skipped, not errored. */
    @Transactional
    public List<SubjectResponse> bulkCreate(UUID tenantId, CreateSubjectsRequest req) {
        return req.subjects().stream().map(spec -> {
            String name = spec.name().trim();
            Subject existing = subjectRepository.findBySchoolIdAndName(tenantId, name).orElse(null);
            if (existing != null) {
                return SubjectResponse.from(existing);
            }
            Subject s = new Subject();
            s.setSchoolId(tenantId);
            s.setName(name);
            s.setCode(spec.code() == null || spec.code().isBlank() ? null : spec.code().trim());
            return SubjectResponse.from(subjectRepository.save(s));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> listSubjects(UUID tenantId) {
        return subjectRepository.findBySchoolIdOrderByName(tenantId).stream()
            .map(SubjectResponse::from)
            .toList();
    }

    Subject getSubjectOrThrow(UUID tenantId, UUID subjectId) {
        Subject s = subjectRepository.findById(subjectId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Subject", subjectId));
        if (!s.getSchoolId().equals(tenantId)) {
            throw AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Subject", subjectId);
        }
        return s;
    }
}
