package com.sihwan.market.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record ConfirmRequest(@NotBlank @Size(max = 100) String paymentId) {}

    public record ConfirmResponse(long orderId, String status) {}
}
