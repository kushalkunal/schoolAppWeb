package in.schoolapp.sync.dto;

import java.util.List;
import java.util.UUID;

/**
 * Per-entry outcome for a {@link SyncPushRequest}. The {@code localId} matches what the client
 * sent so rows can be looked up in the phone's SQLite without a natural-key re-scan.
 */
public record SyncPushResponse(
    int accepted,
    int rejected,
    List<EntryResult> results
) {
    public record EntryResult(
        UUID localId,
        /** The server-assigned attendance record id on success; null when rejected. */
        UUID serverId,
        EntryStatus status,
        String error
    ) {}

    public enum EntryStatus {
        /** New row created on the server. */
        CREATED,
        /** Existing row for the same (student, date) was overwritten. */
        UPDATED,
        /** Entry rejected — student not in section, bad dates, etc. {@code error} has the reason. */
        REJECTED
    }
}
