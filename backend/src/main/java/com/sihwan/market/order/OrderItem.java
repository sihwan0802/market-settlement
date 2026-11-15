package com.sihwan.market.order;

public record OrderItem(long id, long orderId, long productId, int quantity, long amount) {}
