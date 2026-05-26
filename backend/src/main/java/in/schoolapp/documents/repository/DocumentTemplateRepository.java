package in.schoolapp.documents.repository;

import in.schoolapp.documents.DocumentType;
import in.schoolapp.documents.entity.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {

    /** Per-school override lookup. Absent = use the bundled classpath default. */
    Optional<DocumentTemplate> findBySchoolIdAndDocumentType(UUID schoolId, DocumentType type);
}
