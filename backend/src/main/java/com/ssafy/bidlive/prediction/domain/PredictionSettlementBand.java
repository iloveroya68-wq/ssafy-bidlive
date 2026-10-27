package com.ssafy.bidlive.prediction.domain;

import java.math.BigDecimal;

/** 낙찰가 오차 구간에 따른 예측 정산 배수를 정의한다. */
public enum PredictionSettlementBand {
	WITHIN_5("5"),
	WITHIN_10("3"),
	WITHIN_20("1.5"),
	MISS("0"),
	REFUNDED("1");

	private final BigDecimal rewardMultiplier;

	PredictionSettlementBand(String rewardMultiplier) {
		this.rewardMultiplier = new BigDecimal(rewardMultiplier);
	}

	public BigDecimal rewardMultiplier() {
		return rewardMultiplier;
	}
}
