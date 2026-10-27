package com.ssafy.bidlive.auction.application;

import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionStatus;

/** 공개 정책과 선택 인증 정보를 적용한 경매방 상세 조회 결과입니다. */
public record AuctionRoomDetailResult(
	Instant serverTime,
	long auctionId,
	String title,
	AuctionStatus status,
	long reservePrice,
	Instant scheduledAt,
	int durationMinutes,
	int viewerCount,
	boolean seller,
	Integer notificationCount,
	Product product,
	Seller sellerInfo,
	boolean canEnter
) {

	public record Product(
		long productId,
		String title,
		String description,
		String categoryCode,
		String imageUrl
	) {
	}

	public record Seller(
		String nickname,
		Double rating
	) {
	}
}
