package com.ssafy.bidlive.auction.application;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.auction.domain.AuctionStartType;
import com.ssafy.bidlive.auction.domain.AuctionStatus;

/** 검증된 경매방 생성 정보를 저장하는 영속성 계약입니다. */
public interface AuctionRoomCreationPersistence {

	Long save(
		Long productId,
		long reservePrice,
		int durationMinutes,
		AuctionStartType startType,
		OffsetDateTime scheduledAt,
		AuctionStatus status,
		OffsetDateTime createdAt
	);
}
