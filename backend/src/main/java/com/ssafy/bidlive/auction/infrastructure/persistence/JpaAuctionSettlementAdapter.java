package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.auction.domain.VickreySettlementOutcome;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** PostgreSQL에서 BID-07 정산 대상을 잠그고 최종 입찰과 정상 마감 결과를 다룬다. */
@Repository
public class JpaAuctionSettlementAdapter
	implements AuctionSettlementPersistence {

	private static final String LOCK_TARGET = """
		SELECT
			room.auction_room_id,
			room.status,
			room.reserve_price,
			room.ends_at,
			EXISTS (
				SELECT 1
				FROM auction_result result
				WHERE result.auction_room_id = room.auction_room_id
			)
		FROM auction_room room
		WHERE room.auction_room_id = :auctionId
			AND room.deleted_at IS NULL
		FOR UPDATE OF room
		""";

	/** 참여자별 가장 큰 sequence_no 한 건만 계산 입력으로 읽는다. */
	private static final String FIND_FINAL_BIDS = """
		SELECT
			latest.bid_id,
			latest.auction_participant_id,
			latest.member_id,
			latest.nickname,
			latest.amount,
			latest.submitted_at
		FROM (
			SELECT DISTINCT ON (participant.auction_participant_id)
				bid.bid_id,
				participant.auction_participant_id,
				participant.member_id,
				member.nickname,
				bid.amount,
				bid.submitted_at
			FROM bid
			JOIN auction_participant participant
				ON participant.auction_participant_id =
					bid.auction_participant_id
			JOIN member ON member.member_id = participant.member_id
			WHERE participant.auction_room_id = :auctionId
				AND participant.participation_type = 'BIDDER'
			ORDER BY
				participant.auction_participant_id,
				bid.sequence_no DESC,
				bid.bid_id DESC
		) latest
		ORDER BY
			latest.amount DESC,
			latest.submitted_at ASC,
			latest.bid_id ASC
		""";

	private static final String FIND_STORED_RESULT = """
		SELECT
			result.auction_result_id,
			result.result_status,
			result.termination_reason,
			winner.member_id,
			winner.nickname,
			result.highest_bid_amount,
			result.second_highest_bid_amount,
			result.final_price,
			COALESCE(room.ended_at, result.created_at)
		FROM auction_result result
		JOIN auction_room room
			ON room.auction_room_id = result.auction_room_id
		LEFT JOIN auction_participant participant
			ON participant.auction_participant_id =
				result.winner_participant_id
		LEFT JOIN member winner ON winner.member_id = participant.member_id
		WHERE result.auction_room_id = :auctionId
		""";

	private static final String COUNT_BIDS = """
		SELECT
			COUNT(DISTINCT participant.auction_participant_id),
			COUNT(bid.bid_id)
		FROM auction_participant participant
		JOIN bid ON bid.auction_participant_id =
			participant.auction_participant_id
		WHERE participant.auction_room_id = :auctionId
			AND participant.participation_type = 'BIDDER'
		""";

	/** 지갑 잠금 순서를 고정할 수 있도록 회원·입찰 순으로 미정산 홀드를 읽는다. */
	private static final String FIND_OUTSTANDING_HOLDS = """
		SELECT
			bid.bid_id,
			wallet.member_id,
			SUM(ledger.held_delta)
		FROM trade_point_ledger ledger
		JOIN wallet ON wallet.wallet_id = ledger.wallet_id
		JOIN bid ON bid.bid_id = ledger.reference_id
		JOIN auction_participant participant
			ON participant.auction_participant_id =
				bid.auction_participant_id
		WHERE ledger.reference_type = 'BID'
			AND ledger.transaction_type IN (
				'HOLD', 'RELEASE', 'SETTLEMENT'
			)
			AND participant.auction_room_id = :auctionId
		GROUP BY bid.bid_id, wallet.member_id
		HAVING SUM(ledger.held_delta) > 0
		ORDER BY wallet.member_id, bid.bid_id
		""";

	private static final String INSERT_RESULT = """
		INSERT INTO auction_result (
			auction_room_id,
			result_status,
			termination_reason,
			winner_participant_id,
			winning_bid_id,
			highest_bid_amount,
			second_highest_bid_amount,
			final_price,
			valid_bidder_count,
			total_bid_count
		)
		VALUES (
			:auctionId,
			:resultStatus,
			:terminationReason,
			:winnerParticipantId,
			:winningBidId,
			:highestBidAmount,
			:secondHighestBidAmount,
			:finalPrice,
			:validBidderCount,
			:totalBidCount
		)
		ON CONFLICT (auction_room_id) DO NOTHING
		RETURNING auction_result_id
		""";

	private static final String MARK_ENDED = """
		UPDATE auction_room
		SET status = :endedStatus,
			ended_at = CAST(:endedAt AS TIMESTAMPTZ)
		WHERE auction_room_id = :auctionId
			AND status = :liveStatus
			AND deleted_at IS NULL
		""";

	private final EntityManager entityManager;

	public JpaAuctionSettlementAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<SettlementTarget> lockTarget(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(LOCK_TARGET)
			.setParameter("auctionId", auctionId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new SettlementTarget(
			toLong(row[0]),
			(String) row[1],
			toLong(row[2]),
			toInstant(row[3]),
			Boolean.TRUE.equals(row[4])
		));
	}

	@Override
	public List<FinalBid> findFinalBids(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(FIND_FINAL_BIDS)
			.setParameter("auctionId", auctionId)
			.getResultList();
		return rows.stream()
			.map(Object[].class::cast)
			.map(row -> new FinalBid(
				toLong(row[0]),
				toLong(row[1]),
				toLong(row[2]),
				(String) row[3],
				toLong(row[4]),
				toInstant(row[5])
			))
			.toList();
	}

	@Override
	public Optional<StoredResult> findStoredResult(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(FIND_STORED_RESULT)
			.setParameter("auctionId", auctionId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new StoredResult(
			toLong(row[0]),
			(String) row[1],
			(String) row[2],
			nullableLong(row[3]),
			(String) row[4],
			nullableLong(row[5]),
			nullableLong(row[6]),
			nullableLong(row[7]),
			toInstant(row[8])
		));
	}

	@Override
	public List<OutstandingBidHold> findOutstandingBidHolds(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(FIND_OUTSTANDING_HOLDS)
			.setParameter("auctionId", auctionId)
			.getResultList();
		return rows.stream()
			.map(Object[].class::cast)
			.map(row -> new OutstandingBidHold(
				toLong(row[0]),
				toLong(row[1]),
				toLong(row[2])
			))
			.toList();
	}

	@Override
	public BidCounts countBids(long auctionId) {
		Object[] row = (Object[]) entityManager.createNativeQuery(COUNT_BIDS)
			.setParameter("auctionId", auctionId)
			.getSingleResult();
		return new BidCounts(
			((Number) row[0]).intValue(),
			((Number) row[1]).intValue()
		);
	}

	@Override
	public OptionalLong createResultIfAbsent(ResultWrite result) {
		List<?> inserted = entityManager.createNativeQuery(INSERT_RESULT)
			.setParameter("auctionId", result.auctionId())
			.setParameter("resultStatus", result.outcome().name())
			.setParameter(
				"terminationReason",
				terminationReason(result.outcome())
			)
			.setParameter(
				"winnerParticipantId",
				result.winnerParticipantId()
			)
			.setParameter("winningBidId", result.winningBidId())
			.setParameter(
				"highestBidAmount",
				result.highestBidAmount()
			)
			.setParameter(
				"secondHighestBidAmount",
				result.secondHighestBidAmount()
			)
			.setParameter("finalPrice", result.finalPrice())
			.setParameter(
				"validBidderCount",
				result.validBidderCount()
			)
			.setParameter("totalBidCount", result.totalBidCount())
			.getResultList();
		if (inserted.isEmpty()) {
			return OptionalLong.empty();
		}
		return OptionalLong.of(toLong(inserted.getFirst()));
	}

	@Override
	public int markEnded(long auctionId, Instant endedAt) {
		return entityManager.createNativeQuery(MARK_ENDED)
			.setParameter("endedStatus", AuctionStatus.ENDED.name())
			.setParameter("endedAt", endedAt.toString())
			.setParameter("auctionId", auctionId)
			.setParameter("liveStatus", AuctionStatus.LIVE.name())
			.executeUpdate();
	}

	private static String terminationReason(
		VickreySettlementOutcome outcome
	) {
		return outcome == VickreySettlementOutcome.SOLD
			? "NORMAL"
			: "NO_BID";
	}

	private static long toLong(Object value) {
		return ((Number) value).longValue();
	}

	private static Long nullableLong(Object value) {
		return value == null ? null : toLong(value);
	}

	private static Instant toInstant(Object value) {
		return switch (value) {
			case Timestamp timestamp -> timestamp.toInstant();
			case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
			case Instant instant -> instant;
			case null -> null;
			default -> throw new IllegalStateException(
				"Unsupported timestamp type: " + value.getClass().getName()
			);
		};
	}
}
