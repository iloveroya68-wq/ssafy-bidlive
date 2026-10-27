package com.ssafy.bidlive.prediction.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.prediction.application.PredictionSettlementPersistence;
import com.ssafy.bidlive.prediction.domain.PredictionResultStatus;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementBand;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementDecision;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** 정산되지 않은 예측을 조회하고 prediction_result를 멱등하게 저장한다. */
@Repository
public class JpaPredictionSettlementPersistenceAdapter
	implements PredictionSettlementPersistence {

	private static final String FIND_TARGETS_QUERY = """
		SELECT
			prediction.prediction_id,
			participant.member_id,
			prediction.predicted_price,
			prediction.stake_amount
		FROM prediction
		JOIN auction_participant participant
			ON participant.auction_participant_id =
				prediction.auction_participant_id
		WHERE participant.auction_room_id = :auctionId
			AND participant.participation_type = 'PREDICTOR'
			AND NOT EXISTS (
				SELECT 1
				FROM prediction_result result
				WHERE result.prediction_id = prediction.prediction_id
			)
		ORDER BY prediction.prediction_id
		""";

	private static final String FIND_RESULT_QUERY = """
		SELECT
			prediction_result_id,
			prediction_id,
			auction_result_id,
			result_status,
			error_rate,
			reward_multiplier,
			payout_amount
		FROM prediction_result
		WHERE prediction_id = :predictionId
		""";

	private static final String INSERT_RESULT_QUERY = """
		INSERT INTO prediction_result (
			prediction_id,
			auction_result_id,
			result_status,
			error_rate,
			reward_multiplier,
			payout_amount
		)
		VALUES (
			:predictionId,
			:auctionResultId,
			:resultStatus,
			:errorRate,
			:rewardMultiplier,
			:payoutAmount
		)
		ON CONFLICT (prediction_id) DO NOTHING
		RETURNING
			prediction_result_id,
			prediction_id,
			auction_result_id,
			result_status,
			error_rate,
			reward_multiplier,
			payout_amount
		""";

	private final EntityManager entityManager;

	public JpaPredictionSettlementPersistenceAdapter(
		EntityManager entityManager
	) {
		this.entityManager = entityManager;
	}

	@Override
	public List<SettlementTarget> findUnsettledTargets(Long auctionId) {
		return entityManager.createNativeQuery(FIND_TARGETS_QUERY)
			.setParameter("auctionId", auctionId)
			.getResultList()
			.stream()
			.map(this::toSettlementTarget)
			.toList();
	}

	@Override
	public Optional<StoredSettlement> findByPredictionId(Long predictionId) {
		List<?> rows = entityManager.createNativeQuery(FIND_RESULT_QUERY)
			.setParameter("predictionId", predictionId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(toStoredSettlement(rows.getFirst()));
	}

	@Override
	public SaveResult saveIfAbsent(NewSettlement settlement) {
		PredictionSettlementDecision decision = settlement.decision();
		Query insert = entityManager.createNativeQuery(INSERT_RESULT_QUERY)
			.setParameter("predictionId", settlement.predictionId())
			.setParameter("auctionResultId", settlement.auctionResultId())
			.setParameter("resultStatus", decision.resultStatus().name())
			.setParameter("errorRate", decision.errorRate())
			.setParameter(
				"rewardMultiplier",
				decision.rewardMultiplier()
			)
			.setParameter("payoutAmount", decision.payoutAmount());
		List<?> rows = insert.getResultList();
		if (!rows.isEmpty()) {
			return new SaveResult(toStoredSettlement(rows.getFirst()), true);
		}

		StoredSettlement existing = findByPredictionId(
			settlement.predictionId()
		).orElseThrow(() -> new IllegalStateException(
			"Conflicting prediction_result was not visible after insert"
		));
		return new SaveResult(existing, false);
	}

	private SettlementTarget toSettlementTarget(Object value) {
		Object[] row = (Object[]) value;
		return new SettlementTarget(
			asLong(row[0]),
			asLong(row[1]),
			asLong(row[2]),
			asLong(row[3])
		);
	}

	private StoredSettlement toStoredSettlement(Object value) {
		Object[] row = (Object[]) value;
		PredictionResultStatus status = PredictionResultStatus.valueOf(
			(String) row[3]
		);
		BigDecimal errorRate = (BigDecimal) row[4];
		BigDecimal multiplier = (BigDecimal) row[5];
		long payoutAmount = asLong(row[6]);
		PredictionSettlementDecision decision =
			new PredictionSettlementDecision(
				status,
				band(status, multiplier),
				errorRate,
				multiplier,
				payoutAmount
			);
		return new StoredSettlement(
			asLong(row[0]),
			asLong(row[1]),
			asLong(row[2]),
			decision
		);
	}

	private PredictionSettlementBand band(
		PredictionResultStatus status,
		BigDecimal multiplier
	) {
		return switch (status) {
			case REFUNDED -> PredictionSettlementBand.REFUNDED;
			case MISS -> PredictionSettlementBand.MISS;
			case HIT -> bandForHit(multiplier);
		};
	}

	private PredictionSettlementBand bandForHit(BigDecimal multiplier) {
		if (multiplier.compareTo(BigDecimal.ONE) == 0) {
			// 구 정책의 5% 이내 HIT(×1)는 새 정책의 WITHIN_5로 읽되, 저장된 지급액은 보존한다.
			return PredictionSettlementBand.WITHIN_5;
		}
		if (multiplier.compareTo(new BigDecimal("5")) == 0) {
			return PredictionSettlementBand.WITHIN_5;
		}
		if (multiplier.compareTo(new BigDecimal("3")) == 0) {
			return PredictionSettlementBand.WITHIN_10;
		}
		if (multiplier.compareTo(new BigDecimal("1.5")) == 0) {
			return PredictionSettlementBand.WITHIN_20;
		}
		throw new IllegalStateException(
			"Unsupported HIT reward multiplier: " + multiplier
		);
	}

	private Long asLong(Object value) {
		return ((Number) value).longValue();
	}
}
