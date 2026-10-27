package com.ssafy.bidlive.prediction.application;

import java.math.BigDecimal;
import java.util.List;

import com.ssafy.bidlive.prediction.domain.PredictionSettlementBand;

/** PRED-02 적중 밴드별 참여 인원과 지급 합계를 조회하는 영속성 계약이다. */
public interface PredictionResultAggregationPersistence {

	List<BandAggregate> findBandAggregatesByAuctionResultId(
		Long auctionResultId
	);

	record BandAggregate(
		PredictionSettlementBand band,
		long participantCount,
		BigDecimal multiplier,
		long totalPayoutAmount
	) {
		public BandAggregate {
			if (multiplier != null) {
				multiplier = multiplier.stripTrailingZeros();
			}
		}


		public BandAggregate(
			PredictionSettlementBand band,
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
}
