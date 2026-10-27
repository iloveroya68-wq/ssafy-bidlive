package com.ssafy.bidlive.prediction.application;

/** 커밋 후 해당 경매방에 예측 정산 완료를 알리기 위한 내부 이벤트다. */
public record PredictionSettlementCompletedEvent(Long auctionId) {

	public PredictionSettlementCompletedEvent {
		if (auctionId == null || auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
	}
}
