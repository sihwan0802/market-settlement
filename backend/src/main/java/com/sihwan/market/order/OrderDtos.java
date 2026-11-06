package com.sihwan.market.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}

    public record Item(long productId, @Min(1) int quantity) {}

    public record CreateRequest(@NotEmpty @Valid List<Item> items) {}

    public record CreatedResponse(long id, long totalAmount) {}
}
