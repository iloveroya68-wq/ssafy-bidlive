package com.ssafy.bidlive.bid.api.dto;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.bid.application.BidResult;

/** 접수된 입찰의 개인 금액과 회원 전역 포인트 홀드 결과를 표현한다. */
public record BidCreateResponse(
	Long auctionId,
	Long myBidAmount,
	Long previousBidAmount,
	Long additionalHoldAmount,
	Long totalHeldAmount,
	Long availableBalance,
	OffsetDateTime submittedAt
) {

	public static BidCreateResponse from(BidResult result) {
		return new BidCreateResponse(
			result.auctionId(),
			result.myBidAmount(),
			result.previousBidAmount(),
			result.additionalHoldAmount(),
			result.totalHeldAmount(),
			result.availableBalance(),
			result.submittedAt()
		);
	}
}
