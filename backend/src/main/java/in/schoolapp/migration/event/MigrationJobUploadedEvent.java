package in.schoolapp.migration.event;

import java.util.UUID;

/**
 * Fired after a migration job's image has been uploaded + persisted. The async processor picks
 * it up, runs OCR + LLM, and transitions the job to {@code REVIEW} (or {@code FAILED}).
 */
public record MigrationJobUploadedEvent(UUID tenantId, UUID jobId) {}
