package in.schoolapp.fee.structure.repository;

import in.schoolapp.fee.structure.entity.FeeStructureRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FeeStructureRowRepository extends JpaRepository<FeeStructureRow, UUID> {

    List<FeeStructureRow> findByVersionId(UUID versionId);

    List<FeeStructureRow> findByVersionIdAndClassId(UUID versionId, UUID classId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FeeStructureRow r where r.versionId = :versionId")
    void deleteByVersionId(@Param("versionId") UUID versionId);
}
