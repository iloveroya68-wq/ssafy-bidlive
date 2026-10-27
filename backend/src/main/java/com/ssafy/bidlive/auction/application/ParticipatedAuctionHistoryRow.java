package com.ssafy.bidlive.auction.application;

import java.math.BigDecimal;
import java.time.Instant;

/** 조회 계층이 경매·입찰·예측 결과 테이블에서 읽어 온 참여 경매 한 건이다. */
public record ParticipatedAuctionHistoryRow(
	long auctionId,
	String participationType,
	String productTitle,
	String imageUrl,
	String roomStatus,
	String auctionResultStatus,
	String terminationReason,
	Long finalPrice,
	Long myFinalBid,
	Long predictedPrice,
	Long stakeAmount,
	String predictionResultStatus,
	BigDecimal errorRate,
	BigDecimal rewardMultiplier,
	Long predictionPayoutAmount,
	Instant endedAt,
	Instant sortedAt
) {
}
