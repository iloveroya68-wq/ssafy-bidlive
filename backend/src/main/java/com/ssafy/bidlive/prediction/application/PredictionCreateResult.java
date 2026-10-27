package com.ssafy.bidlive.prediction.application;

import java.time.OffsetDateTime;

/** 저장 완료된 낙찰가 예측의 공개 응답 구성 값을 반환한다. */
public record PredictionCreateResult(
	Long predictionId,
	Long auctionId,
	Long predictedPrice,
	Long stakeAmount,
	OffsetDateTime submittedAt
) {
}
