package com.ssafy.bidlive.prediction.domain;

/** 경매 종료 여부와 예측별 결과 수를 조합해 PRED-02 공개 가능 상태를 판단한다. */
public record PredictionSettlementState(
	Long auctionResultId,
	boolean auctionEnded,
	long predictionCount,
	long settledPredictionCount
) {

	public PredictionSettlementState {
		if (predictionCount < 0) {
			throw new IllegalArgumentException(
				"predictionCount must not be negative"
			);
		}
		if (settledPredictionCount < 0
			|| settledPredictionCount > predictionCount) {
			throw new IllegalArgumentException(
				"settledPredictionCount must be between zero and predictionCount"
			);
		}
	}

	public boolean completed() {
		return auctionEnded
			&& auctionResultId != null
			&& predictionCount == settledPredictionCount;
	}
}
