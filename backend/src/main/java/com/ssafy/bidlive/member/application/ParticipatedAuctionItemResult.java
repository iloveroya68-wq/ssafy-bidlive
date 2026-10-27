package com.ssafy.bidlive.member.application;

import java.math.BigDecimal;
import java.time.Instant;

/** 마이페이지에 반환할 사용자의 입찰·예측 참여 경매 항목이다. */
public record ParticipatedAuctionItemResult(
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
