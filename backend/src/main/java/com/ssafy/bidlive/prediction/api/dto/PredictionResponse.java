package com.ssafy.bidlive.prediction.api.dto;

import java.time.OffsetDateTime;

/** 접수된 낙찰가 예측의 공개 응답 계약을 표현합니다. */
public record PredictionResponse(
	Long predictionId,
	Long auctionId,
	Long predictedPrice,
	Long stakeAmount,
	String status,
	OffsetDateTime submittedAt
) {
}
