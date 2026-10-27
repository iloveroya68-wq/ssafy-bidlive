package com.ssafy.bidlive.auction.application;

/** 조회자 본인의 입찰. 낙찰이면 결제액은 2등가라 제출가보다 작다(비크리). */
public record MyBidRow(long amount, boolean winner) {
}
