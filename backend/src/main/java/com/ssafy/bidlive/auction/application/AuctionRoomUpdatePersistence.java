package com.ssafy.bidlive.auction.application;

import java.time.Instant;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.AuctionStartType;

/** 경매방 수정 영속성 계약이다. */
public interface AuctionRoomUpdatePersistence {

	Optional<AuctionRoomUpdateTarget> findUpdateTarget(long auctionId);

	/** 확정된 값을 그대로 쓴다 — "유지" 계산은 유스케이스가 끝낸 뒤 호출한다. */
	void updateAuctionRoom(
		long auctionId,
		long reservePrice,
		int durationMinutes,
		AuctionStartType startType,
		Instant scheduledAt
	);

	/**
	 * 상품만 바뀐 요청에서도 최신 시각을 주기 위해 경매방과 상품의 `updated_at` 중 나중 값을 읽는다.
	 * 경매방을 건드리지 않았으면 그 행의 시각은 예전 값에 머문다.
	 */
	Instant findLastUpdatedAt(long auctionId);
}
