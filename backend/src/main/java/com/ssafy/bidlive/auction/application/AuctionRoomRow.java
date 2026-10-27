package com.ssafy.bidlive.auction.application;

import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionStatus;

/**
 * 조회 계층이 읽어 온 경매방 한 건이다. 판매자 닉네임은 아직 원본이며 공개 규칙 적용은 유스케이스가 한다.
 */
public record AuctionRoomRow(
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
