package com.ssafy.bidlive.prediction.api.dto;

import java.util.Objects;

/** 예측 정산 완료 사실만 전달하는 PREDICTION_SETTLED 메시지다. */
public record PredictionSettledMessage(
	String type,
	Data data
) {

	private static final String TYPE = "PREDICTION_SETTLED";

	public PredictionSettledMessage {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(data, "data");
		if (!TYPE.equals(type)) {
			throw new IllegalArgumentException(
				"type must be PREDICTION_SETTLED"
			);
		}
	}

	public static PredictionSettledMessage completed() {
		return new PredictionSettledMessage(TYPE, new Data());
	}

	public record Data() {
	}
}
