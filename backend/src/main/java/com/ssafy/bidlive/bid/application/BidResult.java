package com.ssafy.bidlive.bid.application;

import java.time.OffsetDateTime;

/** 접수된 입찰의 개인 금액과 회원 전역 지갑 결과를 API 계층에 전달한다. */
public record BidResult(
	Long auctionId,
	Long myBidAmount,
	Long previousBidAmount,
	Long additionalHoldAmount,
	Long totalHeldAmount,
	Long availableBalance,
	OffsetDateTime submittedAt
) {
}
