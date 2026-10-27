package com.ssafy.bidlive.auction.application;

/** 경매 연장 검증과 실시간 상태 갱신을 하나의 원자 연산으로 수행하는 계약이다. */
public interface AuctionExtensionProcessor {

	AuctionExtensionProcessingResult extend(AuctionExtensionCommand command);
}
