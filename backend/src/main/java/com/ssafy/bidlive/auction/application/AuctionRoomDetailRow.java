package com.ssafy.bidlive.auction.application;

import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionStatus;

/** PostgreSQL에서 조회한 경매방·상품·판매자 상세 정보를 운반합니다. */
public record AuctionRoomDetailRow(
	long auctionId,
	long sellerId,
	String title,
	AuctionStatus status,
	long reservePrice,
	Instant scheduledAt,
	int durationMinutes,
	String visibility,
	Instant deletedAt,
	long productId,
	String productTitle,
	String productDescription,
	String productCategoryCode,
	String productImageUrl,
	String productModerationStatus,
	String sellerNickname,
	int notificationCount
) {
}
