package com.ssafy.bidlive.prediction.api.dto;

import java.math.BigDecimal;
import java.util.List;

/** 정산 완료된 경매의 오차 밴드별 예측 인원과 지급 합계를 반환한다. */
public record PredictionResultResponse(
	Long auctionId,
	String settlementStatus,
	List<BandResult> bands
) {

	public PredictionResultResponse {
		bands = List.copyOf(bands);
	}

	public record BandResult(
		String band,
		Long participantCount,
		BigDecimal multiplier,
		Long totalPayoutAmount
	) {
		public BandResult {
			if (multiplier != null) {
				multiplier = multiplier.stripTrailingZeros();
			}
		}


		public BandResult(
			String band,
			Long participantCount,
			int multiplier,
			Long totalPayoutAmount
		) {
			this(
				band,
				participantCount,
				BigDecimal.valueOf(multiplier),
				totalPayoutAmount
			);
		}
	}
}
