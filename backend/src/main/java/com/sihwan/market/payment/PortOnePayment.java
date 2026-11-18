package com.sihwan.market.payment;

public record PortOnePayment(String status, Amount amount, String currency) {

    // cancelled: 지금까지 취소된 금액의 합
    public record Amount(long total, long cancelled) {

        public Amount(long total) {
            this(total, 0);
        }
    }
}
