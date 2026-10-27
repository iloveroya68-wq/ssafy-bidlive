package com.ssafy.bidlive.auction.application;

import java.util.Optional;

/** 대기방 입장 판정에 필요한 경매방 조회 계약이다. */
public interface AuctionRoomJoinPersistence {

	Optional<AuctionRoomJoinTarget> findJoinTarget(long auctionId);
}
