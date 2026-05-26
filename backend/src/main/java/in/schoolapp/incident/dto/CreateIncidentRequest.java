package in.schoolapp.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateIncidentRequest(
    @NotNull UUID studentId,
    @NotBlank String severity,             // MINOR / MAJOR / SEVERE
    @NotBlank String incidentType,         // MERIT / DEMERIT / DISCIPLINE / ACADEMIC
    int     points,
    @NotNull LocalDate occurredOn,
    @NotBlank String description,
    String  actionTaken,
    boolean notifyParent
) {}
