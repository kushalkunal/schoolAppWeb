package in.schoolapp.migration.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of fuzzy-matching an extracted name against the tenant's student roster.
 * <ul>
 *   <li>{@code matchedStudentId} is non-null only when the match is unambiguously high-confidence.</li>
 *   <li>{@code candidates} is always populated (when there are any candidates) so the
 *       review UI can offer alternatives.</li>
 * </ul>
 */
public record MatchResult(
    UUID matchedStudentId,
    double topConfidence,
    List<MatchCandidate> candidates
) {}
