package in.schoolapp.student.repository;

import in.schoolapp.student.entity.StudentDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentDocumentRepository extends JpaRepository<StudentDocument, UUID> {

    List<StudentDocument> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    Optional<StudentDocument> findByIdAndSchoolId(UUID id, UUID schoolId);
}
