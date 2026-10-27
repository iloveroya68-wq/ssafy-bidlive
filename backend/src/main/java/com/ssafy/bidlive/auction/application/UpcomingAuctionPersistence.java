package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 곧 시작할 공개 대기 경매방을 PostgreSQL에서 조회하는 계약입니다. */
public interface UpcomingAuctionPersistence {

	UpcomingAuctionSnapshot findUpcoming(
		Instant startsAfter,
		Instant startsAtOrBefore,
		int limit
	);
}
