package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** BID-07 정산 시작과 최종 결과를 경매방 구독자에게 알리는 포트다. */
public interface AuctionSettlementNotificationPublisher {

	void publishEnded(long auctionId, Instant endedAt);

	void publishSettled(AuctionSettlementResult result);

	void publishNoSale(long auctionId, Instant endedAt);
}
