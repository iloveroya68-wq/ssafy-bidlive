package com.ssafy.bidlive.bid.application;

import java.time.OffsetDateTime;

/** 원자 입찰 처리에 필요한 인증 사용자와 서버 접수 정보를 전달한다. */
public record BidCommand(
	Long auctionId,
	Long memberId,
	Long amount,
	String requestId,
	OffsetDateTime requestedAt
) {

	public BidCommand {
		if (auctionId == null || auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		if (memberId == null || memberId <= 0) {
			throw new IllegalArgumentException("memberId must be positive");
		}
		if (amount == null || amount <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}
		if (requestId == null || requestId.isBlank()) {
			throw new IllegalArgumentException("requestId must not be blank");
		}
		if (requestedAt == null) {
			throw new IllegalArgumentException("requestedAt must not be null");
		}
	}
}
