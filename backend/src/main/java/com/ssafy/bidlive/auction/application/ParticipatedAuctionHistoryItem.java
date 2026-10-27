package com.ssafy.bidlive.auction.application;

import java.math.BigDecimal;
import java.time.Instant;

/** 참여 유형별 공개 결과와 정산 금액을 적용한 참여 경매 항목이다. */
public record ParticipatedAuctionHistoryItem(
	long auctionId,
	String participationType,
	String productTitle,
	String imageUrl,
	String roomStatus,
	String resultType,
	String result,
	String resultLabel,
	Long finalPrice,
	Long myFinalBid,
	Long holdReleasedAmount,
	Long predictedPrice,
	BigDecimal errorRate,
	String errorBand,
	Long stakeAmount,
	Long payoutAmount,
	Long refundAmount,
	Instant endedAt
) {
}
