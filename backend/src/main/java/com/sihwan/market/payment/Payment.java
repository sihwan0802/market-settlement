package com.sihwan.market.payment;

public record Payment(long id, long orderId, String portonePaymentId, long amount, String status) {}
