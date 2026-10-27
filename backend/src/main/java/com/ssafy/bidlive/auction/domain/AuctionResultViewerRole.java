package com.ssafy.bidlive.auction.domain;

/**
 * 결과 조회자의 역할. 명세 「경매 결과 조회」 §규칙의 5값이 단일 소스다.
 *
 * <p>입찰과 예측은 배타적이라(한 방에서 한 사람은 둘 중 하나) {@code WINNING_BIDDER}·
 * {@code LOSING_BIDDER}와 {@code PREDICTOR}가 동시에 성립하지 않는다.
 * 판매자는 참여 자체를 못 하므로 {@code SELLER}가 다른 값보다 앞선다.
 */
public enum AuctionResultViewerRole {
	SELLER,
	WINNING_BIDDER,
	LOSING_BIDDER,
	PREDICTOR,
	SPECTATOR
}
