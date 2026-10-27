package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** Redis가 확정한 최신 연장 상태를 PostgreSQL 경매방에 동기화하는 계약이다. */
public interface AuctionExtensionPersistence {

	/** 더 최신인 연장 결과가 이미 저장됐다면 오래된 결과로 덮어쓰지 않는다. */
	void synchronizeFinalState(
		long auctionId,
		Instant endAt,
		int extensionCount
	);
}
