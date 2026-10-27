package com.ssafy.bidlive.prediction.application;

/** 신규 예측 참여의 경매 식별자와 공개 가능한 누적 참여자 수를 전달한다. */
public record PredictionAcceptedEvent(
	long auctionId,
	long participantCount
) {

	public PredictionAcceptedEvent {
		if (auctionId <= 0L) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		if (participantCount < 0L) {
			throw new IllegalArgumentException(
				"participantCount must not be negative"
			);
		}
	}
}
