package in.schoolapp.migration;

import in.schoolapp.migration.dto.MatchCandidate;
import in.schoolapp.migration.dto.MatchResult;
import in.schoolapp.student.entity.Student;
import in.schoolapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Resolves an LLM-extracted student name (often misspelled, abbreviated, or partial) to one
 * of the tenant's actual {@link Student} records. Uses the trigram index built in V1
 * ({@code idx_students_name_trgm}) for fast fuzzy search.
 * <p>
 * Confidence policy:
 * <ul>
 *   <li>HIGH (≥0.85) — auto-approvable; UI shows green; commit-all skips review</li>
 *   <li>MEDIUM (0.5–0.85) — multiple candidates; UI shows orange dropdown</li>
 *   <li>LOW (&lt;0.5 or no match) — UI shows red, requires manual student selection</li>
 * </ul>
 * Computed via Levenshtein-derived ratio over the top trigram-search results.
 */
@Service
@RequiredArgsConstructor
public class EntityMatchingService {

    private static final int MAX_CANDIDATES = 5;
    private static final double HIGH_CONFIDENCE = 0.85;

    private final StudentRepository studentRepository;

    /** Returns the best match + alternative candidates for an extracted name. */
    @Transactional(readOnly = true)
    public MatchResult match(UUID tenantId, String extractedName, String classHint) {
        if (extractedName == null || extractedName.isBlank()) {
            return new MatchResult(null, 0.0, List.of());
        }
        String query = extractedName.trim();

        var candidates = studentRepository
            .searchBySchoolId(tenantId, query, PageRequest.of(0, MAX_CANDIDATES))
            .getContent();

        if (candidates.isEmpty()) {
            return new MatchResult(null, 0.0, List.of());
        }

        List<MatchCandidate> scored = new ArrayList<>(candidates.size());
        for (Student s : candidates) {
            double score = similarity(query, s.displayName());
            // Class hint boost — if the extracted hint contains the section letter and the
            // student's enrollment matches, bump confidence slightly. Skip for Slice 9
            // simplicity; can plug in by joining StudentEnrollment + Section here.
            scored.add(new MatchCandidate(s.getId(), s.displayName(),
                s.getAdmissionNumber(), score));
        }
        scored.sort(Comparator.comparingDouble(MatchCandidate::confidence).reversed());

        MatchCandidate top = scored.get(0);
        // If the top match is high-confidence and significantly better than #2, return as
        // unambiguous; otherwise return all candidates so the UI can disambiguate.
        boolean unambiguous = top.confidence() >= HIGH_CONFIDENCE
            && (scored.size() == 1 || top.confidence() - scored.get(1).confidence() >= 0.10);

        return new MatchResult(
            unambiguous ? top.studentId() : null,
            top.confidence(),
            scored
        );
    }

    /**
     * Normalised Levenshtein similarity in [0, 1]. 1.0 = identical, 0.0 = totally different.
     * Lowercases + trims to be tolerant of casing/whitespace differences.
     */
    static double similarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        String s = a.toLowerCase().trim();
        String t = b.toLowerCase().trim();
        int max = Math.max(s.length(), t.length());
        if (max == 0) return 1.0;
        int distance = levenshtein(s, t);
        return 1.0 - ((double) distance / max);
    }

    private static int levenshtein(String s, String t) {
        int[] prev = new int[t.length() + 1];
        int[] curr = new int[t.length() + 1];
        for (int j = 0; j <= t.length(); j++) prev[j] = j;
        for (int i = 1; i <= s.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= t.length(); j++) {
                int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[t.length()];
    }
}
