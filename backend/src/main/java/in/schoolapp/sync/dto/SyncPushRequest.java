package in.schoolapp.sync.dto;

import in.schoolapp.attendance.entity.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Batch of attendance records queued on a teacher's phone while offline. Each entry carries
 * a client-generated {@code localId} so the server can return a per-entry result keyed on the
 * same id the client uses in its own SQLite row — makes conflict handling / retry trivial for
 * the mobile client.
 * <p>
 * Idempotency: the server uses the {@code (studentId, date)} unique constraint to upsert. A
 * re-send of the same entry is safe and returns {@code UPDATED}.
 */
public record SyncPushRequest(
    @NotEmpty(message = "entries must not be empty")
    @Size(max = 1000, message = "max 1000 entries per push batch")
    @Valid List<AttendanceEntry> entries
) {
    public record AttendanceEntry(
        /** Client-generated id (any UUID) — echoed back in the response so the client can
         *  correlate without matching on the natural key. */
        @NotNull UUID localId,
        @NotNull UUID sectionId,
        @NotNull UUID studentId,
        @NotNull LocalDate date,
        @NotNull AttendanceStatus status,
        OffsetDateTime arrivalTime,
        String note
    ) {}
}
