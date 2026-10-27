package com.ssafy.bidlive.prediction.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.prediction.application.PredictionParticipationPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** PREDICTOR 참여자와 prediction을 PostgreSQL에 멱등하게 저장한다. */
@Repository
public class JpaPredictionParticipationPersistenceAdapter
	implements PredictionParticipationPersistence {

	private static final String LOCK_IDEMPOTENCY_KEY_QUERY = """
		SELECT pg_advisory_xact_lock(
			hashtextextended(:idempotencyKey, 0)
		)
		""";

	private static final String FIND_BY_IDEMPOTENCY_KEY_QUERY = """
		SELECT
			prediction.prediction_id,
			participant.auction_room_id,
			participant.member_id,
			prediction.predicted_price,
			prediction.stake_amount,
			prediction.idempotency_key,
			prediction.submitted_at
		FROM prediction
		JOIN auction_participant participant
			ON participant.auction_participant_id =
				prediction.auction_participant_id
		WHERE prediction.idempotency_key = :idempotencyKey
		""";

	private static final String FIND_BY_AUCTION_AND_MEMBER_QUERY = """
		SELECT
			prediction.prediction_id,
			participant.auction_room_id,
			participant.member_id,
			prediction.predicted_price,
			prediction.stake_amount,
			prediction.idempotency_key,
			prediction.submitted_at
		FROM prediction
		JOIN auction_participant participant
			ON participant.auction_participant_id =
				prediction.auction_participant_id
		WHERE participant.auction_room_id = :auctionId
			AND participant.member_id = :memberId
			AND participant.participation_type = 'PREDICTOR'
		""";

	private static final String INSERT_PARTICIPANT_QUERY = """
		INSERT INTO auction_participant (
			auction_room_id,
			member_id,
			participation_type,
			participated_at
		)
		VALUES (
			:auctionId,
			:memberId,
			'PREDICTOR',
			:submittedAt
		)
		RETURNING auction_participant_id
		""";

	private static final String INSERT_PREDICTION_QUERY = """
		INSERT INTO prediction (
			auction_participant_id,
			predicted_price,
			stake_amount,
			idempotency_key,
			submitted_at
		)
		VALUES (
			:participantId,
			:predictedPrice,
			:stakeAmount,
			:idempotencyKey,
			:submittedAt
		)
		RETURNING prediction_id
		""";

	private final EntityManager entityManager;

	public JpaPredictionParticipationPersistenceAdapter(
		EntityManager entityManager
	) {
		this.entityManager = entityManager;
	}

	@Override
	public void lockIdempotencyKey(String idempotencyKey) {
		entityManager.createNativeQuery(LOCK_IDEMPOTENCY_KEY_QUERY)
			.setParameter("idempotencyKey", idempotencyKey)
			.getSingleResult();
	}

	@Override
	public Optional<StoredPrediction> findByIdempotencyKey(
		String idempotencyKey
	) {
		List<?> rows = entityManager.createNativeQuery(
			FIND_BY_IDEMPOTENCY_KEY_QUERY
		)
			.setParameter("idempotencyKey", idempotencyKey)
			.getResultList();
		return first(rows);
	}

	@Override
	public Optional<StoredPrediction> findByAuctionIdAndMemberId(
		Long auctionId,
		Long memberId
	) {
		List<?> rows = entityManager.createNativeQuery(
			FIND_BY_AUCTION_AND_MEMBER_QUERY
		)
			.setParameter("auctionId", auctionId)
			.setParameter("memberId", memberId)
			.getResultList();
		return first(rows);
	}

	@Override
	public StoredPrediction save(NewPrediction prediction) {
		Query participantInsert = entityManager.createNativeQuery(
			INSERT_PARTICIPANT_QUERY
		)
			.setParameter("auctionId", prediction.auctionId())
			.setParameter("memberId", prediction.memberId())
			.setParameter("submittedAt", prediction.submittedAt());
		Long participantId = asLong(participantInsert.getSingleResult());

		Query predictionInsert = entityManager.createNativeQuery(
			INSERT_PREDICTION_QUERY
		)
			.setParameter("participantId", participantId)
			.setParameter("predictedPrice", prediction.predictedPrice())
			.setParameter("stakeAmount", prediction.stakeAmount())
			.setParameter("idempotencyKey", prediction.idempotencyKey())
			.setParameter("submittedAt", prediction.submittedAt());
		Long predictionId = asLong(predictionInsert.getSingleResult());

		return new StoredPrediction(
			predictionId,
			prediction.auctionId(),
			prediction.memberId(),
			prediction.predictedPrice(),
			prediction.stakeAmount(),
			prediction.idempotencyKey(),
			prediction.submittedAt()
		);
	}

	private Optional<StoredPrediction> first(List<?> rows) {
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(toStoredPrediction((Object[]) rows.getFirst()));
	}

	private StoredPrediction toStoredPrediction(Object[] row) {
		return new StoredPrediction(
			asLong(row[0]),
			asLong(row[1]),
			asLong(row[2]),
			asLong(row[3]),
			asLong(row[4]),
			(String) row[5],
			asOffsetDateTime(row[6])
		);
	}

	private OffsetDateTime asOffsetDateTime(Object value) {
		if (value instanceof OffsetDateTime offsetDateTime) {
			return offsetDateTime;
		}
		if (value instanceof Instant instant) {
			return instant.atOffset(ZoneOffset.UTC);
		}
		if (value instanceof Timestamp timestamp) {
			return timestamp.toInstant().atOffset(ZoneOffset.UTC);
		}
		throw new IllegalStateException(
			"Unsupported submitted_at type: " + value.getClass().getName()
		);
	}

	private Long asLong(Object value) {
		return ((Number) value).longValue();
	}
}
