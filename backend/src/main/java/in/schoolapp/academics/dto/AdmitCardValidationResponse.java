package in.schoolapp.academics.dto;

import java.util.List;

/**
 * Pre-flight checks before admit cards are generated. {@code ready} is true when there are no
 * blocking warnings (no participating classes / no active students). Soft warnings (missing
 * subjects or schedule) are surfaced but do not block generation.
 */
public record AdmitCardValidationResponse(
    boolean ready,
    int activeStudents,
    boolean hasParticipatingClasses,
    boolean hasSubjectsConfigured,
    boolean hasSchedule,
    List<String> warnings
) {}
