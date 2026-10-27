package com.ssafy.bidlive.auction.application;

import java.math.BigDecimal;

/**
 * 밴드별 집계 한 줄. <b>개별 예측가는 절대 담지 않는다</b> —
 * 명세 §규칙: "종료 후 공개 가능한 집계 분포만 포함하고 다른 사용자의 개별 예측가는 반환하지 않는다".
 *
 * <p>{@code band}는 저장된 {@code result_status}·{@code reward_multiplier}에서 읽은 값이다.
 * 배수만으로 묶으면 {@code REFUNDED}(×1)가 오차 밴드와 섞일 수 있으므로 상태까지 분리한다.
 */
public record PredictionBandRow(
	String band,
	long participantCount,
	BigDecimal multiplier,
	long totalPayoutAmount
) {
	public PredictionBandRow {
		if (multiplier != null) {
			multiplier = multiplier.stripTrailingZeros();
		}
	}


	public PredictionBandRow(
		String band,
		long participantCount,
		int multiplier,
		long totalPayoutAmount
	) {
		this(
			band,
			participantCount,
			BigDecimal.valueOf(multiplier),
			totalPayoutAmount
		);
	}
}
