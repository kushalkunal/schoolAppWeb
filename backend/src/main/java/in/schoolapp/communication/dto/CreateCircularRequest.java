package in.schoolapp.communication.dto;

import in.schoolapp.communication.entity.CircularTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateCircularRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 4000) String body,
    @NotNull CircularTargetType targetType,
    /** Required for CLASSES / SECTIONS / STUDENTS. Ignored for ALL_PARENTS. */
    List<UUID> targetIds,
    /** BCP-47 language tag; defaults to "en". Translation module hook. */
    String language
) {}
