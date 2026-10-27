package com.ssafy.bidlive.auction.application;

import java.util.Optional;

/** 실시간 경매의 상태와 현재 마감 시각을 같은 조회에서 읽는 출력 계약이다. */
public interface AuctionTimeSyncStateReader {

	Optional<AuctionTimeSyncState> findByAuctionId(long auctionId);
}
