package in.schoolapp.fee.repository;

import in.schoolapp.fee.entity.FeeHead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeHeadRepository extends JpaRepository<FeeHead, UUID> {

    Optional<FeeHead> findBySchoolIdAndName(UUID schoolId, String name);

    List<FeeHead> findBySchoolIdAndActiveTrueOrderByName(UUID schoolId);

    Optional<FeeHead> findByIdAndSchoolId(UUID id, UUID schoolId);
}
