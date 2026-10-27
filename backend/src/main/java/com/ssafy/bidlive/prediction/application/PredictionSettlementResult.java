package com.ssafy.bidlive.prediction.application;

/** 한 경매에서 새로 생성·건너뛴 예측 정산과 포인트 합계를 반환한다. */
public record PredictionSettlementResult(
	Long auctionId,
	int targetCount,
	int settledCount,
	int skippedCount,
	long totalStakedPoints,
	long totalPayoutPoints
) {
}
