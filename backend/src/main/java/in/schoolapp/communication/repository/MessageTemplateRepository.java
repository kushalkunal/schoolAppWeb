package in.schoolapp.communication.repository;

import in.schoolapp.communication.entity.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, UUID> {

    Optional<MessageTemplate> findBySchoolIdAndTemplateKey(UUID schoolId, String templateKey);

    List<MessageTemplate> findBySchoolIdOrderByTemplateKeyAsc(UUID schoolId);
}
