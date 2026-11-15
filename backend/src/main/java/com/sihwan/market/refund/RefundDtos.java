package com.sihwan.market.refund;

import jakarta.validation.constraints.Size;

public final class RefundDtos {
    private RefundDtos() {}

    public record RefundRequest(long orderItemId, @Size(max = 200) String reason) {}

    public record RefundResponse(long refundId, String orderStatus) {}
}
