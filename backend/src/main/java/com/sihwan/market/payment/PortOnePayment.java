package com.sihwan.market.payment;

public record PortOnePayment(String status, Amount amount, String currency) {

    public record Amount(long total) {}
}
