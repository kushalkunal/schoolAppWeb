package in.schoolapp.fee;

import in.schoolapp.audit.AuditLogger;
import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.fee.dto.CreateFeeHeadRequest;
import in.schoolapp.fee.dto.FeeHeadResponse;
import in.schoolapp.fee.entity.FeeHead;
import in.schoolapp.fee.repository.FeeHeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD for the per-tenant fee-head catalog ("Tuition", "Transport", "Library", …). Deletion is
 * soft — we flip {@code active=false} so historical payments keep a valid FK.
 */
@Service
@RequiredArgsConstructor
public class FeeHeadService {

    private final FeeHeadRepository repository;
    private final AuditLogger auditLogger;

    @Transactional
    public FeeHeadResponse create(UUID tenantId, CreateFeeHeadRequest req) {
        String name = req.name().trim();
        repository.findBySchoolIdAndName(tenantId, name).ifPresent(existing -> {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                "Fee head with name '" + name + "' already exists");
        });
        FeeHead h = new FeeHead();
        h.setSchoolId(tenantId);
        h.setName(name);
        h.setActive(true);
        h = repository.save(h);
        auditLogger.logCreate(tenantId, "FeeHead", h.getId(), Map.of("name", name));
        return FeeHeadResponse.from(h);
    }

    @Transactional(readOnly = true)
    public List<FeeHeadResponse> list(UUID tenantId) {
        return repository.findBySchoolIdAndActiveTrueOrderByName(tenantId).stream()
            .map(FeeHeadResponse::from)
            .toList();
    }

    @Transactional
    public FeeHeadResponse update(UUID tenantId, UUID id, CreateFeeHeadRequest req) {
        FeeHead h = require(tenantId, id);
        String oldName = h.getName();
        String newName = req.name().trim();
        if (!oldName.equals(newName)) {
            repository.findBySchoolIdAndName(tenantId, newName).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Fee head with name '" + newName + "' already exists");
                }
            });
            h.setName(newName);
            repository.save(h);
            auditLogger.logUpdate(tenantId, "FeeHead", id,
                Map.of("name", oldName), Map.of("name", newName));
        }
        return FeeHeadResponse.from(h);
    }

    @Transactional
    public void deactivate(UUID tenantId, UUID id) {
        FeeHead h = require(tenantId, id);
        if (!h.isActive()) return;
        h.setActive(false);
        repository.save(h);
        auditLogger.logDelete(tenantId, "FeeHead", id, Map.of("name", h.getName()));
    }

    private FeeHead require(UUID tenantId, UUID id) {
        return repository.findByIdAndSchoolId(id, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "FeeHead", id));
    }
}
