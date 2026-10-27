package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 확정된 경매 마감 시각을 클라이언트 시간 동기화 메시지로 발행하는 출력 계약이다. */
public interface AuctionTimeSyncPublisher {

	void publish(long auctionId, Instant endAt);
}
