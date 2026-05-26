package in.schoolapp.fee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFeeHeadRequest(
    @NotBlank @Size(max = 100) String name
) {}
