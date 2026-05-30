package in.schoolapp.vault;

import in.schoolapp.common.AppException;
import in.schoolapp.common.ErrorCode;
import in.schoolapp.common.TenantContext;
import in.schoolapp.storage.FileStorageService;
import in.schoolapp.student.StudentAccessGuard;
import in.schoolapp.vault.entity.VaultDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VaultService {

    private final VaultDocumentRepository repo;
    private final FileStorageService storage;
    private final StudentAccessGuard studentAccessGuard;

    @Transactional
    public VaultDocument upload(UUID tenantId, UUID studentId, String docType,
                                String fileName, String mimeType, byte[] bytes, String notes) {
        studentAccessGuard.assertInTenant(tenantId, studentId);
        var stored = storage.store(
            "tenants/" + tenantId + "/vault/" + studentId + "/" + System.currentTimeMillis() + "-" + fileName,
            bytes, mimeType);
        VaultDocument d = new VaultDocument();
        d.setSchoolId(tenantId);
        d.setStudentId(studentId);
        d.setDocType(docType);
        d.setFileName(fileName);
        d.setFileUrl(stored.url());
        d.setMimeType(mimeType);
        d.setSizeBytes((long) bytes.length);
        d.setUploadedById(TenantContext.getStaffId());
        d.setNotes(notes);
        return repo.save(d);
    }

    @Transactional(readOnly = true)
    public List<VaultDocument> listForStudent(UUID tenantId, UUID studentId) {
        studentAccessGuard.assertInTenant(tenantId, studentId);
        return repo.findByStudentIdOrderByUploadedAtDesc(studentId);
    }

    @Transactional
    public void delete(UUID tenantId, UUID id) {
        VaultDocument d = repo.findByIdAndSchoolId(id, tenantId)
            .orElseThrow(() -> AppException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "VaultDocument", id));
        repo.delete(d);
    }
}
