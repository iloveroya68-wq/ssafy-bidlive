package com.ssafy.bidlive.prediction.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionResultAggregationPersistence;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementBand;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 정산 결과를 오차 밴드별 인원과 지급 합계로 집계한다. */
@Repository
public class JpaPredictionResultAggregationPersistenceAdapter
	implements PredictionResultAggregationPersistence {

	private static final String FIND_BAND_AGGREGATES_QUERY = """
		WITH settlement_band (
			band,
			result_status,
			reward_multiplier,
			display_order
		) AS (
			VALUES
				('WITHIN_5', 'HIT', 5, 1),
				('WITHIN_10', 'HIT', 3, 2),
				('WITHIN_20', 'HIT', 1.5, 3),
				('MISS', 'MISS', 0, 4)
		)
		SELECT
			band.band,
			COUNT(result.prediction_result_id) AS participant_count,
			band.reward_multiplier,
			COALESCE(SUM(result.payout_amount), 0) AS total_payout_amount
		FROM settlement_band band
		LEFT JOIN prediction_result result
			ON result.auction_result_id = :auctionResultId
			AND result.result_status = band.result_status
			AND (
				result.reward_multiplier = band.reward_multiplier
				OR (
					band.band = 'WITHIN_5'
					AND result.reward_multiplier = 1
				)
			)
		GROUP BY
			band.band,
			band.reward_multiplier,
			band.display_order
		ORDER BY band.display_order
		""";

	private final EntityManager entityManager;

	public JpaPredictionResultAggregationPersistenceAdapter(
		EntityManager entityManager
	) {
		this.entityManager = entityManager;
	}

	@Override
	public List<BandAggregate> findBandAggregatesByAuctionResultId(
		Long auctionResultId
	) {
		return entityManager
			.createNativeQuery(FIND_BAND_AGGREGATES_QUERY)
			.setParameter("auctionResultId", auctionResultId)
			.getResultList()
			.stream()
			.map(this::toBandAggregate)
			.toList();
	}

	private BandAggregate toBandAggregate(Object value) {
		Object[] row = (Object[]) value;
		return new BandAggregate(
			PredictionSettlementBand.valueOf((String) row[0]),
			asLong(row[1]),
			new BigDecimal(row[2].toString()),
			asLong(row[3])
		);
	}

	private long asLong(Object value) {
		return ((Number) value).longValue();
	}
}
