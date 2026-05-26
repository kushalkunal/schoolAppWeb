package in.schoolapp.admissions.dto;

import jakarta.validation.constraints.Size;

public record OfferRequest(
    /** URL of the offer-letter PDF (typically rendered via DocumentService). */
    @Size(max = 2000) String offerLetterUrl
) {}
