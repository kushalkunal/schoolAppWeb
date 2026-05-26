package in.schoolapp.fee.structure.repository;

import in.schoolapp.fee.structure.entity.FeeStructureTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FeeStructureTermRepository extends JpaRepository<FeeStructureTerm, UUID> {

    List<FeeStructureTerm> findByVersionIdOrderByTermNumberAsc(UUID versionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FeeStructureTerm t where t.versionId = :versionId")
    void deleteByVersionId(@Param("versionId") UUID versionId);
}
