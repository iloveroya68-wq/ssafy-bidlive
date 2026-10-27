package com.ssafy.bidlive.member.application;

import java.time.Instant;

/** 판매자 액션까지 계산한 마이페이지 개최 경매 항목이다. */
public record HostedAuctionItemResult(
	long auctionId,
	Long dealId,
	String productTitle,
	String imageUrl,
	String roomStatus,
	String resultType,
	Long finalPrice,
	String winnerNickname,
	String dealStatus,
	boolean actionRequired,
	String actionCode,
	String actionLabel,
	long bidCount,
	long participantCount,
	long predictionParticipantCount,
	Instant scheduledAt,
	Instant startedAt,
	Instant endedAt
) {
}
