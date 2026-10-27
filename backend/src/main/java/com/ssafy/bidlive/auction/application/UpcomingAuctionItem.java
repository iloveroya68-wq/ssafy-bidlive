package com.ssafy.bidlive.auction.application;

import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionStatus;

/** 공개 경매 정보와 Redis에서 조회한 현재 대기 인원 수를 결합합니다. */
public record UpcomingAuctionItem(
	long auctionId,
	String productTitle,
	String imageUrl,
	Instant scheduledAt,
	long waitingCount,
	AuctionStatus status
) {
}
