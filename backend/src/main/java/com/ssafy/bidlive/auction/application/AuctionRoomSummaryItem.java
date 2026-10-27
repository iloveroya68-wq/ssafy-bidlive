package com.ssafy.bidlive.auction.application;

import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionStatus;

/**
 * 목록 카드 한 건의 공개 가능한 정보다. 개별 입찰 금액·최고가·입찰자 정보는 담지 않는다.
 */
public record AuctionRoomSummaryItem(
	Long auctionId,
	String title,
	String thumbnailUrl,
	String categoryCode,
	String sellerNickname,
	long reservePrice,
	long bidCount,
	long bidParticipantCount,
	long predictionParticipantCount,
	Instant scheduledAt,
	Instant endAt,
	Long soldPrice,
	Instant endedAt,
	AuctionStatus status,
	int viewerCount
) {
}
