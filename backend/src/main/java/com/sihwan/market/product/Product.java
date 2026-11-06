package com.sihwan.market.product;

public record Product(long id, long sellerId, String name, long price, String status, boolean flashSale, int stock) {}
