package in.schoolapp.migration.dto;

import java.util.UUID;

public record MatchCandidate(
    UUID studentId,
    String displayName,
    String admissionNumber,
    double confidence
) {}
