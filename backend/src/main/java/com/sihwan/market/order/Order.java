package com.sihwan.market.order;

public record Order(long id, long buyerId, String status, long totalAmount) {}
