package in.schoolapp.vault;

import in.schoolapp.vault.entity.VaultDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaultDocumentRepository extends JpaRepository<VaultDocument, UUID> {
    Optional<VaultDocument> findByIdAndSchoolId(UUID id, UUID schoolId);
    List<VaultDocument> findByStudentIdOrderByUploadedAtDesc(UUID studentId);
}
