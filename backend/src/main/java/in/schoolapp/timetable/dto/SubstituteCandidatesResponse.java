package in.schoolapp.timetable.dto;

import java.util.List;
import java.util.UUID;

/**
 * Decision-support payload for assigning a substitute to one period on one date. Instead of a
 * blind list of all teachers, the picker can show:
 *
 * <ul>
 *   <li>{@code absent}    — teachers marked ABSENT/LEAVE today (candidates to be <em>replaced</em>),</li>
 *   <li>{@code available} — present teachers who are <em>free</em> at this day+period (best substitutes),</li>
 *   <li>{@code busy}      — present teachers already teaching or substituting at this slot.</li>
 * </ul>
 */
public record SubstituteCandidatesResponse(
    List<Candidate> absent,
    List<Candidate> available,
    List<Candidate> busy
) {
    public record Candidate(UUID staffId, String name, String role, String note) {}
}
