package com.ssafy.bidlive.auction.application;

import java.util.Optional;

/** 경매방 상세 조회에 필요한 PostgreSQL 조회 계약입니다. */
public interface AuctionRoomDetailPersistence {

	Optional<AuctionRoomDetailRow> findByAuctionId(long auctionId);
}
