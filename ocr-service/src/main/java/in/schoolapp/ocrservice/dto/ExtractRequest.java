package in.schoolapp.ocrservice.dto;

import in.schoolapp.ocrservice.JobType;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /extract}. Backend serializes the image bytes as base64 (rather than
 * multipart) to keep the API uniform with the eventual cloud function deployment shape.
 */
public record ExtractRequest(
    @NotNull JobType jobType,
    @NotNull String imageBase64
) {}
