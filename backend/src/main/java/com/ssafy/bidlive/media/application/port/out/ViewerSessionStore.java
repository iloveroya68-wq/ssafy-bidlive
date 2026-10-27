package com.ssafy.bidlive.media.application.port.out;

import java.time.Instant;

/** 경매별 활성 STOMP 세션의 활동 시각 저장, 만료 정리와 집계를 수행하는 계약입니다. */
public interface ViewerSessionStore {

	void record(long auctionId, String stompSessionId, Instant lastSeenAt);

	void remove(long auctionId, String stompSessionId);

	long count(long auctionId);

	long removeExpiredAndCount(long auctionId, Instant expirationCutoff);
}
