package com.ssafy.bidlive.auction.application;

import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionStatus;

/** 영속성 계층에서 조회한 곧 시작할 경매방 한 건을 전달합니다. */
public record UpcomingAuctionRow(
	long auctionId,
	String productTitle,
	String imageUrl,
	Instant scheduledAt,
	AuctionStatus status
) {
}
