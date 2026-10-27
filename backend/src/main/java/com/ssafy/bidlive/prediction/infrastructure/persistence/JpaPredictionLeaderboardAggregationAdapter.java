package com.ssafy.bidlive.prediction.infrastructure.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionLeaderboardAggregationPersistence;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardEntry;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardSnapshot;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** PostgreSQL에서 일일 예측 성과를 한 번 집계해 날짜별 순위 스냅샷으로 교체한다. */
@Repository
public class JpaPredictionLeaderboardAggregationAdapter
	implements PredictionLeaderboardAggregationPersistence {

	private static final int ADVISORY_LOCK_NAMESPACE = 704_169;

	private static final String LOCK_RANKING_DATE_QUERY = """
		SELECT pg_advisory_xact_lock(
			:lockNamespace,
			:rankingDateKey
		)
		""";

	private static final String DELETE_SNAPSHOT_QUERY = """
		DELETE FROM prediction_leaderboard
		WHERE ranking_date = :rankingDate
		""";

	private static final String INSERT_SNAPSHOT_QUERY = """
		WITH aggregated AS (
			SELECT
				participant.member_id,
				COUNT(*)::int AS prediction_count,
				COUNT(*) FILTER (
					WHERE result.result_status = 'HIT'
				)::int AS rewarded_count,
				COUNT(*) FILTER (
					WHERE result.result_status = 'MISS'
				)::int AS lost_count,
				SUM(prediction.stake_amount)::bigint
					AS total_stake_amount,
				SUM(result.payout_amount)::bigint
					AS total_payout_amount,
				AVG(result.error_rate)
					AS average_error_rate,
				MIN(result.error_rate)
					AS best_error_rate
			FROM prediction_result result
			JOIN prediction
				ON prediction.prediction_id = result.prediction_id
			JOIN auction_participant participant
				ON participant.auction_participant_id =
					prediction.auction_participant_id
			WHERE result.created_at >= :rangeStart
				AND result.created_at < :rangeEnd
				AND result.result_status <> 'REFUNDED'
			GROUP BY participant.member_id
			HAVING SUM(prediction.stake_amount) > 0
		),
		ranked AS (
			SELECT
				aggregated.*,
				ROW_NUMBER() OVER (
					ORDER BY
						total_payout_amount::numeric
							/ total_stake_amount DESC,
						prediction_count DESC,
						member_id ASC
				) AS rank_no
			FROM aggregated
		)
		INSERT INTO prediction_leaderboard (
			member_id,
			ranking_date,
			rank_no,
			score,
			prediction_count,
			rewarded_count,
			lost_count,
			total_stake_amount,
			total_payout_amount,
			average_error_rate,
			best_error_rate,
			created_at
		)
		SELECT
			member_id,
			:rankingDate,
			rank_no,
			total_payout_amount,
			prediction_count,
			rewarded_count,
			lost_count,
			total_stake_amount,
			total_payout_amount,
			average_error_rate,
			best_error_rate,
			:calculatedAt
		FROM ranked
		""";

	private static final String FIND_SNAPSHOT_QUERY = """
		SELECT
			leaderboard.rank_no,
			leaderboard.member_id,
			member.nickname,
			leaderboard.prediction_count,
			leaderboard.total_stake_amount,
			leaderboard.total_payout_amount
		FROM prediction_leaderboard leaderboard
		JOIN member
			ON member.member_id = leaderboard.member_id
		WHERE leaderboard.ranking_date = :rankingDate
		ORDER BY leaderboard.rank_no ASC
		""";

	private final EntityManager entityManager;

	public JpaPredictionLeaderboardAggregationAdapter(
		EntityManager entityManager
	) {
		this.entityManager = entityManager;
	}

	@Override
	public PredictionLeaderboardSnapshot replaceSnapshot(
		LocalDate rankingDate,
		OffsetDateTime rangeStart,
		OffsetDateTime rangeEnd,
		OffsetDateTime calculatedAt
	) {
		lockRankingDate(rankingDate);
		deleteSnapshot(rankingDate);
		insertSnapshot(
			rankingDate,
			rangeStart,
			rangeEnd,
			calculatedAt
		);
		return findSnapshot(rankingDate, calculatedAt);
	}

	private void lockRankingDate(LocalDate rankingDate) {
		entityManager.createNativeQuery(LOCK_RANKING_DATE_QUERY)
			.setParameter("lockNamespace", ADVISORY_LOCK_NAMESPACE)
			.setParameter(
				"rankingDateKey",
				Math.toIntExact(rankingDate.toEpochDay())
			)
			.getSingleResult();
	}

	private void deleteSnapshot(LocalDate rankingDate) {
		entityManager.createNativeQuery(DELETE_SNAPSHOT_QUERY)
			.setParameter("rankingDate", rankingDate)
			.executeUpdate();
	}

	private void insertSnapshot(
		LocalDate rankingDate,
		OffsetDateTime rangeStart,
		OffsetDateTime rangeEnd,
		OffsetDateTime calculatedAt
	) {
		entityManager.createNativeQuery(INSERT_SNAPSHOT_QUERY)
			.setParameter("rankingDate", rankingDate)
			.setParameter("rangeStart", rangeStart)
			.setParameter("rangeEnd", rangeEnd)
			.setParameter("calculatedAt", calculatedAt)
			.executeUpdate();
	}

	private PredictionLeaderboardSnapshot findSnapshot(
		LocalDate rankingDate,
		OffsetDateTime calculatedAt
	) {
		List<?> rows = entityManager
			.createNativeQuery(FIND_SNAPSHOT_QUERY)
			.setParameter("rankingDate", rankingDate)
			.getResultList();
		List<PredictionLeaderboardEntry> entries = rows.stream()
			.map(this::toEntry)
			.toList();
		return new PredictionLeaderboardSnapshot(
			rankingDate,
			calculatedAt,
			entries
		);
	}

	private PredictionLeaderboardEntry toEntry(Object rowValue) {
		Object[] row = (Object[]) rowValue;
		long stakedPoints = asLong(row[4]);
		long payoutPoints = asLong(row[5]);
		return new PredictionLeaderboardEntry(
			asLong(row[0]),
			asLong(row[1]),
			(String) row[2],
			((Number) row[3]).intValue(),
			stakedPoints,
			payoutPoints,
			payoutRate(stakedPoints, payoutPoints)
		);
	}

	private BigDecimal payoutRate(
		long stakedPoints,
		long payoutPoints
	) {
		return BigDecimal.valueOf(payoutPoints)
			.multiply(BigDecimal.valueOf(100))
			.divide(
				BigDecimal.valueOf(stakedPoints),
				1,
				RoundingMode.HALF_UP
			);
	}

	private long asLong(Object value) {
		return ((Number) value).longValue();
	}
}
