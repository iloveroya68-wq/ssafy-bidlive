package com.ssafy.bidlive.auction.application;

import java.time.Instant;
import java.util.Optional;

/** 경매 방송 시작 영속성 계약이다. */
public interface AuctionRoomStartPersistence {

	Optional<AuctionRoomStartTarget> findStartTarget(long auctionId);

	/**
	 * 아직 `WAITING`인 방만 `LIVE`로 전이하고 시작·종료 시각을 채운다.
	 *
	 * @return 전이한 행 수. 동시에 두 요청이 들어오면 뒤늦은 쪽이 0을 받는다 —
	 *     명세의 "중복 시작 요청은 두 번 처리하지 않는다"를 DB에서 보장하는 지점이다
	 */
	int markStarted(long auctionId, Instant startedAt, Instant endAt);
}
