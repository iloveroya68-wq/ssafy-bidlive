package com.ssafy.bidlive.bid.application;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.bid.domain.BidFailureCode;

/** 원자 입찰 처리의 승인 결과 또는 명세상 실패 코드를 반환한다. */
public record BidProcessingResult(
	boolean accepted,
	Long previousBidAmount,
	Long currentBidAmount,
	Long additionalHoldAmount,
	Long totalHeldAmount,
	Long availableBalance,
	OffsetDateTime submittedAt,
	Long bidCount,
	Long participantCount,
	BidFailureCode failureCode
) {

	public BidProcessingResult {
		if (accepted && failureCode != null) {
			throw new IllegalArgumentException("accepted result must not have a failure code");
		}
		if (!accepted && failureCode == null) {
			throw new IllegalArgumentException("rejected result must have a failure code");
		}
	}

	public static BidProcessingResult accepted(
		long previousBidAmount,
		long currentBidAmount,
		long additionalHoldAmount,
		long totalHeldAmount,
		long availableBalance,
		OffsetDateTime submittedAt,
		long bidCount,
		long participantCount
	) {
		return new BidProcessingResult(
			true,
			previousBidAmount,
			currentBidAmount,
			additionalHoldAmount,
			totalHeldAmount,
			availableBalance,
			submittedAt,
			bidCount,
			participantCount,
			null
		);
	}

	public static BidProcessingResult rejected(BidFailureCode failureCode) {
		return new BidProcessingResult(
			false,
			null,
			null,
			null,
			null,
			null,
			null,
			null,
			null,
			failureCode
		);
	}
}
