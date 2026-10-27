package com.ssafy.bidlive.auction.application;

import java.math.BigDecimal;

/**
 * 예측자 개인 정산.
 *
 * <p>결과값은 {@code HIT}/{@code MISS}/{@code REFUNDED}다 — 입찰의 {@code WON}/{@code LOST}와
 * 섞어 쓰지 않는다(S15P11A704-323에서 통일 확정). <b>정산기가 저장한 값을 그대로 옮긴다.</b>
 *
 * <p>{@code payoutAmount}는 {@code stakeAmount × multiplier}라 <b>원금이 포함된 값</b>이다
 * (PRED-03 §정산 규칙).
 */
public record PredictionSettlement(
	long predictionId,
	long predictedPrice,
	long stakeAmount,
	String result,
	String errorBand,
	BigDecimal multiplier,
	Long payoutAmount,
	Long refundAmount
) {
}
