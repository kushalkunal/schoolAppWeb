package in.schoolapp.fee.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Bulk opening-balance upload for paper-register migration (gap analysis §7, "Opening Balance
 * Problem"). Single transaction — either all balances are recorded or none.
 */
public record OpeningBalanceRequest(
    @NotEmpty @Valid List<Entry> balances
) {
    public record Entry(
        @NotNull UUID studentId,
        @PositiveOrZero long amountPaise,
        @Size(max = 500) String note
    ) {}
}
