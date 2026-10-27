package com.ssafy.bidlive.bid.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.bid.application.BidPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** BIDDER 참여자와 모든 상향 입찰 이력을 PostgreSQL에 순서대로 저장한다. */
@Repository
public class JpaBidPersistenceAdapter implements BidPersistence {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	private static final String LOCK_IDEMPOTENCY_KEY_QUERY = """
		SELECT pg_advisory_xact_lock(
			hashtextextended(CAST(:idempotencyKey AS TEXT), 0)
		)
		""";

	private static final String LOCK_PARTICIPATION_QUERY = """
		SELECT pg_advisory_xact_lock(
			hashtextextended(
				CONCAT(
					'BID_PARTICIPATION:',
					CAST(:auctionId AS TEXT),
					':',
					CAST(:memberId AS TEXT)
				),
				0
			)
		)
		""";

	private static final String FIND_BY_IDEMPOTENCY_KEY_QUERY = """
		SELECT
			bid.bid_id,
			participant.auction_room_id,
			participant.member_id,
			bid.amount,
			bid.sequence_no,
			bid.idempotency_key,
			bid.submitted_at,
			COALESCE((
				SELECT previous.amount
				FROM bid previous
				WHERE previous.auction_participant_id =
					bid.auction_participant_id
					AND previous.sequence_no < bid.sequence_no
				ORDER BY previous.sequence_no DESC
				LIMIT 1
			), 0) AS previous_bid_amount
		FROM bid
		JOIN auction_participant participant
			ON participant.auction_participant_id =
				bid.auction_participant_id
		WHERE bid.idempotency_key = :idempotencyKey
		""";

	private static final String HAS_PREDICTION_PARTICIPATION_QUERY = """
		SELECT COUNT(*)
		FROM auction_participant
		WHERE auction_room_id = :auctionId
			AND member_id = :memberId
			AND participation_type = 'PREDICTOR'
		""";

	private static final String FIND_PARTICIPANT_QUERY = """
		SELECT
			auction_participant_id,
			participation_type
		FROM auction_participant
		WHERE auction_room_id = :auctionId
			AND member_id = :memberId
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
			'BIDDER',
			:submittedAt
		)
		RETURNING auction_participant_id
		""";

	private static final String FIND_LATEST_BID_QUERY = """
		SELECT sequence_no, amount
		FROM bid
		WHERE auction_participant_id = :participantId
		ORDER BY sequence_no DESC
		LIMIT 1
		""";

	private static final String INSERT_BID_QUERY = """
		INSERT INTO bid (
			auction_participant_id,
			amount,
			sequence_no,
			idempotency_key,
			submitted_at
		)
		VALUES (
			:participantId,
			:amount,
			:sequenceNo,
			:idempotencyKey,
			:submittedAt
		)
		RETURNING bid_id
		""";

	private final EntityManager entityManager;

	public JpaBidPersistenceAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public void lockIdempotencyKey(String idempotencyKey) {
		entityManager.createNativeQuery(LOCK_IDEMPOTENCY_KEY_QUERY)
			.setParameter("idempotencyKey", idempotencyKey)
			.getSingleResult();
	}

	@Override
	public void lockParticipation(Long auctionId, Long memberId) {
		entityManager.createNativeQuery(LOCK_PARTICIPATION_QUERY)
			.setParameter("auctionId", auctionId)
			.setParameter("memberId", memberId)
			.getSingleResult();
	}

	@Override
	public Optional<StoredBid> findByIdempotencyKey(String idempotencyKey) {
		List<?> rows = entityManager
			.createNativeQuery(FIND_BY_IDEMPOTENCY_KEY_QUERY)
			.setParameter("idempotencyKey", idempotencyKey)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(toStoredBid((Object[]) rows.getFirst()));
	}

	@Override
	public boolean hasPredictionParticipation(
		Long auctionId,
		Long memberId
	) {
		Number count = (Number) entityManager
			.createNativeQuery(HAS_PREDICTION_PARTICIPATION_QUERY)
			.setParameter("auctionId", auctionId)
			.setParameter("memberId", memberId)
			.getSingleResult();
		return count.longValue() > 0;
	}

	@Override
	public StoredBid save(NewBid bid) {
		Long participantId = findOrCreateParticipant(bid);
		LatestBid latest = findLatestBid(participantId);
		int sequenceNo = Math.addExact(latest.sequenceNo(), 1);

		Query insert = entityManager.createNativeQuery(INSERT_BID_QUERY)
			.setParameter("participantId", participantId)
			.setParameter("amount", bid.amount())
			.setParameter("sequenceNo", sequenceNo)
			.setParameter("idempotencyKey", bid.idempotencyKey())
			.setParameter("submittedAt", bid.submittedAt());
		Long bidId = asLong(insert.getSingleResult());

		return new StoredBid(
			bidId,
			bid.auctionId(),
			bid.memberId(),
			bid.amount(),
			latest.amount(),
			sequenceNo,
			bid.idempotencyKey(),
			bid.submittedAt()
		);
	}

	private Long findOrCreateParticipant(NewBid bid) {
		List<?> rows = entityManager
			.createNativeQuery(FIND_PARTICIPANT_QUERY)
			.setParameter("auctionId", bid.auctionId())
			.setParameter("memberId", bid.memberId())
			.getResultList();
		if (!rows.isEmpty()) {
			Object[] row = (Object[]) rows.getFirst();
			if (!"BIDDER".equals(row[1])) {
				throw new IllegalStateException(
					"Prediction participant cannot store a bid"
				);
			}
			return asLong(row[0]);
		}

		return asLong(entityManager
			.createNativeQuery(INSERT_PARTICIPANT_QUERY)
			.setParameter("auctionId", bid.auctionId())
			.setParameter("memberId", bid.memberId())
			.setParameter("submittedAt", bid.submittedAt())
			.getSingleResult());
	}

	private LatestBid findLatestBid(Long participantId) {
		List<?> rows = entityManager
			.createNativeQuery(FIND_LATEST_BID_QUERY)
			.setParameter("participantId", participantId)
			.getResultList();
		if (rows.isEmpty()) {
			return new LatestBid(0, 0L);
		}
		Object[] row = (Object[]) rows.getFirst();
		return new LatestBid(
			((Number) row[0]).intValue(),
			asLong(row[1])
		);
	}

	private StoredBid toStoredBid(Object[] row) {
		return new StoredBid(
			asLong(row[0]),
			asLong(row[1]),
			asLong(row[2]),
			asLong(row[3]),
			asLong(row[7]),
			((Number) row[4]).intValue(),
			(String) row[5],
			asOffsetDateTime(row[6])
		);
	}

	private OffsetDateTime asOffsetDateTime(Object value) {
		if (value instanceof OffsetDateTime offsetDateTime) {
			return offsetDateTime.toInstant()
				.atZone(KOREA_ZONE)
				.toOffsetDateTime();
		}
		if (value instanceof Instant instant) {
			return instant.atZone(KOREA_ZONE).toOffsetDateTime();
		}
		if (value instanceof Timestamp timestamp) {
			return timestamp.toInstant()
				.atZone(KOREA_ZONE)
				.toOffsetDateTime();
		}
		throw new IllegalStateException(
			"Unsupported submitted_at type: " + value.getClass().getName()
		);
	}

	private Long asLong(Object value) {
		return ((Number) value).longValue();
	}

	private record LatestBid(int sequenceNo, long amount) {
	}
}
