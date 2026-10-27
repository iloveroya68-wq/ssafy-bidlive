package com.ssafy.bidlive.common.application;

/** 경매별 Redis 공개 상태를 한 시점의 불변 스냅샷으로 조회하는 계약입니다. */
public interface RealtimeAuctionStateReader {

	RealtimeAuctionStateSnapshot read(long auctionId);
}
