package in.schoolapp.school.dto;

import java.util.List;

/**
 * Drives the onboarding wizard's completion bar and the list of pending next actions. Not stored
 * directly — derived from live entity state (class count, staff count, etc.) so it stays
 * truthful even if {@code school.settings.onboarding} drifts.
 */
public record OnboardingStatusResponse(
    int percentComplete,
    List<StepStatus> steps,
    List<String> pendingStepKeys
) {
    public record StepStatus(String key, String label, boolean complete) {}
}
