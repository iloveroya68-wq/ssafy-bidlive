package com.ssafy.bidlive.prediction.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** PRED-01 예측 가격·스테이크·클라이언트 멱등성 키를 입력받는다. */
public record PredictionCreateRequest(
	@NotNull @Positive Long predictedPrice,
	@NotNull @Positive Long stakeAmount,
	@NotNull UUID clientRequestId
) {
}
