package in.schoolapp.infirmary.dto;

import in.schoolapp.infirmary.entity.InfirmaryVisit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record VisitResponse(
    UUID id, UUID studentId, OffsetDateTime visitedAt,
    String complaint, String treatment, String medicineGiven,
    BigDecimal temperatureC, Integer pulse,
    boolean sentHome, boolean parentNotified, String notes
) {
    public static VisitResponse from(InfirmaryVisit v) {
        return new VisitResponse(
            v.getId(), v.getStudentId(), v.getVisitedAt(),
            v.getComplaint(), v.getTreatment(), v.getMedicineGiven(),
            v.getTemperatureC(), v.getPulse(),
            v.isSentHome(), v.isParentNotified(), v.getNotes());
    }
}
