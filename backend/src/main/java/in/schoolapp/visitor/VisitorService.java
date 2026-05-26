package in.schoolapp.visitor;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.visitor.dto.CreateVisitorRequest;
import in.schoolapp.visitor.dto.VisitorResponse;
import in.schoolapp.visitor.entity.Visitor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VisitorService {

    private final VisitorRepository repo;

    @Transactional
    public VisitorResponse checkIn(UUID tenantId, CreateVisitorRequest req) {
        Visitor v = new Visitor();
        v.setSchoolId(tenantId);
        v.setName(req.name());
        v.setPhone(req.phone());
        v.setPurpose(req.purpose());
        v.setHostStaffId(req.hostStaffId());
        v.setHostStudentId(req.hostStudentId());
        v.setBadgeNumber(req.badgeNumber());
        v.setPhotoUrl(req.photoUrl());
        v.setNotes(req.notes());
        v.setCreatedById(TenantContext.getStaffId());
        return VisitorResponse.from(repo.save(v));
    }

    @Transactional
    public VisitorResponse checkOut(UUID tenantId, UUID visitorId) {
        Visitor v = repo.findByIdAndSchoolId(visitorId, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Visitor", visitorId));
        if (v.getOutAt() == null) {
            v.setOutAt(OffsetDateTime.now());
            repo.save(v);
        }
        return VisitorResponse.from(v);
    }

    @Transactional(readOnly = true)
    public Page<VisitorResponse> list(UUID tenantId, int page, int size) {
        return repo.findBySchoolIdOrderByInAtDesc(tenantId, PageRequest.of(page, size))
            .map(VisitorResponse::from);
    }

    @Transactional(readOnly = true)
    public List<VisitorResponse> listOpen(UUID tenantId) {
        return repo.findBySchoolIdAndOutAtIsNullOrderByInAtDesc(tenantId).stream()
            .map(VisitorResponse::from).toList();
    }
}
