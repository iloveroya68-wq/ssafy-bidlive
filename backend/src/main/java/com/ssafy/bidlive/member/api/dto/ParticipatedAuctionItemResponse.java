package com.ssafy.bidlive.member.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ssafy.bidlive.member.application.ParticipatedAuctionItemResult;

/** 참여 경매 목록의 개별 입찰·예측 결과 응답이다. */
public record ParticipatedAuctionItemResponse(
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

	public static ParticipatedAuctionItemResponse from(
		ParticipatedAuctionItemResult result
	) {
		return new ParticipatedAuctionItemResponse(
			result.auctionId(),
			result.participationType(),
			result.productTitle(),
			result.imageUrl(),
			result.roomStatus(),
			result.resultType(),
			result.result(),
			result.resultLabel(),
			result.finalPrice(),
			result.myFinalBid(),
			result.holdReleasedAmount(),
			result.predictedPrice(),
			result.errorRate(),
			result.errorBand(),
			result.stakeAmount(),
			result.payoutAmount(),
			result.refundAmount(),
			result.endedAt()
		);
	}
}
