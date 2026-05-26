package in.schoolapp.incident.dto;

import in.schoolapp.incident.entity.Incident;

import java.time.LocalDate;
import java.util.UUID;

public record IncidentResponse(
    UUID id, UUID studentId, String severity, String incidentType, int points,
    LocalDate occurredOn, String description, String actionTaken, boolean parentNotified
) {
    public static IncidentResponse from(Incident i) {
        return new IncidentResponse(
            i.getId(), i.getStudentId(), i.getSeverity(), i.getIncidentType(), i.getPoints(),
            i.getOccurredOn(), i.getDescription(), i.getActionTaken(), i.isParentNotified());
    }
}
