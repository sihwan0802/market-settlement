package com.sihwan.market.settlement;

import java.time.LocalDate;

/** 결제나 환불이 있었는데 정산 행이 없는 (판매자, 날짜). */
public record MissingSettlement(long sellerId, LocalDate date) {}
