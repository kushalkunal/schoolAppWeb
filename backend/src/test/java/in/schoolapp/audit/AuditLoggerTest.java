package in.schoolapp.audit;

import in.schoolapp.audit.entity.AuditAction;
import in.schoolapp.audit.entity.AuditLog;
import in.schoolapp.audit.repository.AuditLogRepository;
import in.schoolapp.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLoggerTest {

    @Mock AuditLogRepository repository;

    @InjectMocks AuditLogger logger;

    @BeforeEach
    void setTenant() {
        TenantContext.set(UUID.randomUUID(), UUID.randomUUID(), "PRINCIPAL");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void logCreate_writesRowWithActorAndRoleFromTenantContext() {
        UUID tenant = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(repository.save(org.mockito.ArgumentMatchers.any(AuditLog.class)))
            .thenAnswer(i -> i.getArgument(0));

        logger.logCreate(tenant, "Student", entityId, Map.of("firstName", "Aarav"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getSchoolId()).isEqualTo(tenant);
        assertThat(saved.getEntityType()).isEqualTo("Student");
        assertThat(saved.getEntityId()).isEqualTo(entityId);
        assertThat(saved.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(saved.getNewValues()).containsEntry("firstName", "Aarav");
        assertThat(saved.getChangedByRole()).isEqualTo("PRINCIPAL");
        assertThat(saved.getChangedById()).isNotNull();
    }

    @Test
    void logUpdate_capturesBothOldAndNewValues() {
        UUID tenant = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(repository.save(org.mockito.ArgumentMatchers.any(AuditLog.class)))
            .thenAnswer(i -> i.getArgument(0));

        logger.logUpdate(tenant, "Student", entityId,
            Map.of("firstName", "A"), Map.of("firstName", "B"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(saved.getOldValues()).containsEntry("firstName", "A");
        assertThat(saved.getNewValues()).containsEntry("firstName", "B");
    }

    @Test
    void logAction_packagesDetailsIntoNewValuesWithActionKey() {
        UUID tenant = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(repository.save(org.mockito.ArgumentMatchers.any(AuditLog.class)))
            .thenAnswer(i -> i.getArgument(0));

        logger.logAction(tenant, "Exam", entityId, "PUBLISH", Map.of("examName", "Midterm"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo(AuditAction.ACTION);
        assertThat(saved.getNewValues()).containsEntry("action", "PUBLISH");
        assertThat(saved.getNewValues()).containsEntry("examName", "Midterm");
    }

    @Test
    void repositoryException_isSwallowed_noPropagate() {
        UUID tenant = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(repository.save(org.mockito.ArgumentMatchers.any(AuditLog.class)))
            .thenThrow(new RuntimeException("db down"));

        // No throw — caller's transaction must not be rolled back by audit failure.
        logger.logCreate(tenant, "Student", entityId, Map.of("x", "y"));
    }

    @Test
    void logDelete_storesPriorStateAsOldValues() {
        UUID tenant = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(repository.save(org.mockito.ArgumentMatchers.any(AuditLog.class)))
            .thenAnswer(i -> i.getArgument(0));

        logger.logDelete(tenant, "Staff", entityId, Map.of("role", "CLASS_TEACHER"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo(AuditAction.DELETE);
        assertThat(saved.getOldValues()).containsEntry("role", "CLASS_TEACHER");
        assertThat(saved.getNewValues()).isNull();
    }

    @Test
    void logWithoutTenantContext_stillWrites_withNullActor() {
        TenantContext.clear();
        UUID tenant = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(repository.save(org.mockito.ArgumentMatchers.any(AuditLog.class)))
            .thenAnswer(i -> i.getArgument(0));

        // Scheduled cron paths have no TenantContext — audit must still succeed.
        logger.logCreate(tenant, "Alert", entityId, Map.of("severity", "HIGH"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getChangedById()).isNull();
        assertThat(captor.getValue().getChangedByRole()).isNull();
    }

    @Test
    void logAction_withNullDetails_stillRecordsAction() {
        UUID tenant = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(repository.save(org.mockito.ArgumentMatchers.any(AuditLog.class)))
            .thenAnswer(i -> i.getArgument(0));

        logger.logAction(tenant, "Alert", entityId, "DISMISS", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNewValues()).containsEntry("action", "DISMISS");
    }
}
