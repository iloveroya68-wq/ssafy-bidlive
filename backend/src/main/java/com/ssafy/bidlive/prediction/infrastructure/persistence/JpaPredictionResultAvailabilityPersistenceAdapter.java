package com.ssafy.bidlive.prediction.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.prediction.application.PredictionResultAvailabilityPersistence;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementState;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 경매 상태와 예측 결과 건수를 한 스냅샷으로 조회해 정산 완료 여부를 제공한다. */
@Repository
public class JpaPredictionResultAvailabilityPersistenceAdapter
	implements PredictionResultAvailabilityPersistence {

	private static final String FIND_STATE_QUERY = """
		SELECT
			(room.status = 'ENDED') AS auction_ended,
			result.auction_result_id,
			COUNT(prediction.prediction_id) AS prediction_count,
			COUNT(prediction_result.prediction_result_id) FILTER (
				WHERE prediction_result.auction_result_id =
					result.auction_result_id
			) AS settled_prediction_count
		FROM auction_room room
		LEFT JOIN auction_result result
			ON result.auction_room_id = room.auction_room_id
		LEFT JOIN auction_participant participant
			ON participant.auction_room_id = room.auction_room_id
			AND participant.participation_type = 'PREDICTOR'
		LEFT JOIN prediction
			ON prediction.auction_participant_id =
				participant.auction_participant_id
		LEFT JOIN prediction_result
			ON prediction_result.prediction_id =
				prediction.prediction_id
		WHERE room.auction_room_id = :auctionId
		GROUP BY room.status, result.auction_result_id
		""";

	private final EntityManager entityManager;

	public JpaPredictionResultAvailabilityPersistenceAdapter(
		EntityManager entityManager
	) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<PredictionSettlementState> findStateByAuctionId(
		Long auctionId
	) {
		List<?> rows = entityManager.createNativeQuery(FIND_STATE_QUERY)
			.setParameter("auctionId", auctionId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(toState(rows.getFirst()));
	}

	private PredictionSettlementState toState(Object value) {
		Object[] row = (Object[]) value;
		return new PredictionSettlementState(
			row[1] == null ? null : asLong(row[1]),
			(Boolean) row[0],
			asLong(row[2]),
			asLong(row[3])
		);
	}

	private Long asLong(Object value) {
		return ((Number) value).longValue();
	}
}
