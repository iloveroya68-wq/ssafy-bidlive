package com.ssafy.bidlive.auction.application;

import java.util.Optional;

/** 경매방 논리 삭제 영속성 계약이다. */
public interface AuctionRoomDeletePersistence {

	Optional<AuctionRoomDeletionTarget> findDeletionTarget(long auctionId);

	/**
	 * 아직 삭제되지 않은 방만 삭제 표시한다.
	 *
	 * @return 표시한 행 수. 동시에 두 요청이 들어오면 뒤늦은 쪽이 0을 받는다
	 */
	int markDeleted(long auctionId);
}
