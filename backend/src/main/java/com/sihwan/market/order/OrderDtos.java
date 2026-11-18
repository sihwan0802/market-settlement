package com.sihwan.market.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.time.OffsetDateTime;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}

    public record Item(long productId, @Min(1) int quantity) {}

    public record CreateRequest(@NotEmpty @Valid List<Item> items) {}

    public record CreatedResponse(long id, long totalAmount) {}

    public record OrderRow(long id, String status, long totalAmount, OffsetDateTime orderedAt) {}

    public record ItemRow(long id, long orderId, long productId, String productName, long unitPrice, int quantity, long amount) {}

    public record ItemView(long id, long productId, String productName, long unitPrice, int quantity, long amount) {}

    public record OrderView(long id, String status, long totalAmount, OffsetDateTime orderedAt, List<ItemView> items) {}

    /** 판매자가 보는 판매 내역 한 줄 */
    public record SellerLine(long orderId, long itemId, String status, OffsetDateTime orderedAt, long productId,
                             String productName, long unitPrice, int quantity, long amount) {}
}
