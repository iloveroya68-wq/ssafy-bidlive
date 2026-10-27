package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 회원 도메인에 공개할 수 있도록 결과 타입과 닉네임 정책을 적용한 개최 경매 항목이다. */
public record HostedAuctionHistoryItem(
	long auctionId,
	Long dealId,
	String productTitle,
	String imageUrl,
	String roomStatus,
	String resultType,
	Long finalPrice,
	String winnerNickname,
	String dealStatus,
	long bidCount,
	long participantCount,
	long predictionParticipantCount,
	Instant scheduledAt,
	Instant startedAt,
	Instant endedAt
) {
}
