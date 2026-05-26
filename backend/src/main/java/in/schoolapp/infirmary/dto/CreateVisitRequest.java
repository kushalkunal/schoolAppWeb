package in.schoolapp.infirmary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateVisitRequest(
    @NotNull UUID studentId,
    @NotBlank String complaint,
    String treatment,
    String medicineGiven,
    BigDecimal temperatureC,
    Integer pulse,
    boolean sentHome,
    String notes
) {}
