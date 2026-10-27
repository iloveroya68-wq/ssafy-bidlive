package com.ssafy.bidlive.auction.application;

import java.math.BigDecimal;

/**
 * 조회자 본인의 예측과 정산.
 *
 * <p><b>오차율을 담지 않는다.</b> 밴드는 정산기가 저장해 둔 {@code result_status}·
 * {@code reward_multiplier}에서 읽는다 — 여기서 다시 계산하면 정산 시점의 정책과 갈린다.
 * (실제로 갈렸다: {@code error_rate}는 <b>퍼센트</b>인데 비율로 읽어 전원 미적중이 됐다, MR !226 리뷰 1.)
 *
 * <p>정산 전이면 {@code resultStatus}가 null이다 — {@code prediction}은 있는데
 * {@code prediction_result}가 아직 없는 구간.
 */
public record MyPredictionRow(
	long predictionId,
	long predictedPrice,
	long stakeAmount,
	String resultStatus,
	BigDecimal rewardMultiplier,
	Long payoutAmount
) {

	public MyPredictionRow(
		long predictionId,
		long predictedPrice,
		long stakeAmount,
		String resultStatus,
		int rewardMultiplier,
		Long payoutAmount
	) {
		this(
			predictionId,
			predictedPrice,
			stakeAmount,
			resultStatus,
			BigDecimal.valueOf(rewardMultiplier),
			payoutAmount
		);
	}
}
