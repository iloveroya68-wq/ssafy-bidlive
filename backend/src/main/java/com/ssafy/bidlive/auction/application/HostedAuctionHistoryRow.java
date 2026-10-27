package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 조회 계층이 여러 경매 관련 테이블에서 읽어 온 개최 경매 한 건이다. */
public record HostedAuctionHistoryRow(
	long auctionId,
	Long dealId,
	String productTitle,
	String imageUrl,
	String roomStatus,
	String resultStatus,
	String terminationReason,
	Long finalPrice,
	String winnerNickname,
	String dealStatus,
	long bidCount,
	long participantCount,
	long predictionParticipantCount,
	Instant scheduledAt,
	Instant startedAt,
	Instant endedAt,
	Instant sortedAt
) {
}
