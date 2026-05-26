package in.schoolapp.cafeteria.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(
    @NotNull UUID studentId,
    @NotEmpty @Valid List<Item> items,
    @Size(max = 500) String notes
) {
    public record Item(
        @NotNull UUID menuItemId,
        @Positive int quantity
    ) {}
}
