package com.ssafy.bidlive.prediction.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 일일 성과의 필터·순위·멱등 저장 규칙을 검증한다. */
@SpringBootTest
@Transactional
class PredictionLeaderboardAggregationServiceIntegrationTest {

	private static final LocalDate RANKING_DATE =
		LocalDate.of(2026, 7, 18);
	private static final OffsetDateTime RANGE_START =
		OffsetDateTime.parse("2026-07-18T00:00:00+09:00");
	private static final OffsetDateTime RANGE_END =
		OffsetDateTime.parse("2026-07-19T00:00:00+09:00");

	@Autowired
	private PredictionLeaderboardAggregationService service;

	@Autowired
	private PredictionLeaderboardQueryPersistence queryPersistence;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void replacesDailySnapshotUsingExactRatioAndStableTieBreakers() {
		FixtureContext context = createContext();
		Long firstMember = insertMember("leaderboard-first");
		Long secondMember = insertMember("leaderboard-second");
		Long thirdMember = insertMember("leaderboard-third");
		Long fourthMember = insertMember("leaderboard-fourth");
		Long fifthMember = insertMember("leaderboard-fifth");
		Long refundedMember = insertMember("leaderboard-refunded");
		Long outsideMember = insertMember("leaderboard-outside");

		insertPredictionResult(
			context,
			firstMember,
			100L,
			150L,
			"HIT",
			RANGE_START
		);
		insertPredictionResult(
			context,
			firstMember,
			100L,
			250L,
			"HIT",
			RANGE_END.minusNanos(1_000)
		);
		insertPredictionResult(
			context,
			secondMember,
			100L,
			200L,
			"HIT",
			RANGE_START.plusHours(1)
		);
		insertPredictionResult(
			context,
			thirdMember,
			50L,
			100L,
			"HIT",
			RANGE_START.plusHours(2)
		);
		insertPredictionResult(
			context,
			fourthMember,
			300L,
			400L,
			"HIT",
			RANGE_START.plusHours(3)
		);
		insertPredictionResult(
			context,
			fifthMember,
			3_000L,
			3_999L,
			"HIT",
			RANGE_START.plusHours(4)
		);
		insertPredictionResult(
			context,
			refundedMember,
			100L,
			100L,
			"REFUNDED",
			RANGE_START.plusHours(5)
		);
		insertPredictionResult(
			context,
			outsideMember,
			100L,
			500L,
			"HIT",
			RANGE_END
		);

		PredictionLeaderboardSnapshot first =
			service.aggregate(RANKING_DATE);
		PredictionLeaderboardSnapshot rerun =
			service.aggregate(RANKING_DATE);

		assertThat(first.rankingDate()).isEqualTo(RANKING_DATE);
		assertThat(first.entries()).extracting(
			PredictionLeaderboardEntry::memberId
		).containsExactly(
			firstMember,
			secondMember,
			thirdMember,
			fourthMember,
			fifthMember
		);
		assertThat(first.entries()).extracting(
			PredictionLeaderboardEntry::rank
		).containsExactly(1L, 2L, 3L, 4L, 5L);
		assertThat(first.entries()).extracting(
			PredictionLeaderboardEntry::settledPredictionCount
		).containsExactly(2, 1, 1, 1, 1);
		assertThat(first.entries()).extracting(
			PredictionLeaderboardEntry::payoutRate
		).containsExactly(
			new BigDecimal("200.0"),
			new BigDecimal("200.0"),
			new BigDecimal("200.0"),
			new BigDecimal("133.3"),
			new BigDecimal("133.3")
		);
		assertThat(rerun.entries()).isEqualTo(first.entries());
		assertThat(snapshotRowCount()).isEqualTo(5);
		assertThat(distinctSnapshotMemberCount()).isEqualTo(5);

		PredictionLeaderboardTop10Snapshot top10 =
			queryPersistence.findTop10(
				RANKING_DATE,
				rerun.calculatedAt()
			);
		assertThat(top10.items()).isEqualTo(rerun.entries());
		assertThat(top10.hasNext()).isFalse();
		assertThat(top10.nextCursor()).isNull();

		PredictionLeaderboardPage firstPage =
			queryPersistence.findPage(RANKING_DATE, null, 2);
		PredictionLeaderboardCursor firstCursor =
			PredictionLeaderboardCursor.decode(firstPage.nextCursor());
		PredictionLeaderboardPage secondPage =
			queryPersistence.findPage(RANKING_DATE, firstCursor, 2);

		assertThat(firstPage.items()).extracting(
			PredictionLeaderboardEntry::rank
		).containsExactly(1L, 2L);
		assertThat(firstPage.hasNext()).isTrue();
		assertThat(firstCursor).isEqualTo(
			new PredictionLeaderboardCursor(
				RANKING_DATE,
				rerun.calculatedAt(),
				2L,
				secondMember
			)
		);
		assertThat(secondPage.items()).extracting(
			PredictionLeaderboardEntry::rank
		).containsExactly(3L, 4L);
		assertThat(secondPage.hasNext()).isTrue();
		assertThat(queryPersistence.findMyRank(
			RANKING_DATE,
			rerun.calculatedAt(),
			firstMember
		)).contains(rerun.entries().getFirst());
		assertThat(queryPersistence.findMyRank(
			RANKING_DATE,
			rerun.calculatedAt(),
			outsideMember
		)).isEmpty();
	}

	private FixtureContext createContext() {
		String suffix = suffix();
		Long sellerId = insertMember("pl-seller");
		Long categoryId = insert("""
			INSERT INTO product_category (code, name)
			VALUES (?, 'Prediction Leaderboard')
			RETURNING category_id
			""", "PL_" + suffix);
		return new FixtureContext(sellerId, categoryId);
	}

	private void insertPredictionResult(
		FixtureContext context,
		Long memberId,
		long stakeAmount,
		long payoutAmount,
		String resultStatus,
		OffsetDateTime createdAt
	) {
		String suffix = suffix();
		Long productId = insert("""
			INSERT INTO product (
				seller_member_id,
				category_id,
				title,
				description
			)
			VALUES (?, ?, ?, 'Leaderboard aggregation test')
			RETURNING product_id
			""", context.sellerId(), context.categoryId(), "Product-" + suffix);
		Long auctionId = insert("""
			INSERT INTO auction_room (
				product_id,
				reserve_price,
				duration_minutes,
				start_type,
				status
			)
			VALUES (?, 1, 10, 'IMMEDIATE', 'ENDED')
			RETURNING auction_room_id
			""", productId);
		Long participantId = insert("""
			INSERT INTO auction_participant (
				auction_room_id,
				member_id,
				participation_type
			)
			VALUES (?, ?, 'PREDICTOR')
			RETURNING auction_participant_id
			""", auctionId, memberId);
		Long predictionId = insert("""
			INSERT INTO prediction (
				auction_participant_id,
				predicted_price,
				stake_amount,
				idempotency_key
			)
			VALUES (?, 10000, ?, ?)
			RETURNING prediction_id
			""", participantId, stakeAmount, "pl-prediction-" + suffix);
		Long auctionResultId = insert("""
			INSERT INTO auction_result (
				auction_room_id,
				result_status,
				termination_reason,
				valid_bidder_count,
				total_bid_count
			)
			VALUES (?, 'NO_SALE', 'NO_BID', 0, 0)
			RETURNING auction_result_id
			""", auctionId);
		BigDecimal errorRate = "REFUNDED".equals(resultStatus)
			? null
			: new BigDecimal("1.000000");
		jdbcTemplate.update("""
			INSERT INTO prediction_result (
				prediction_id,
				auction_result_id,
				result_status,
				error_rate,
				reward_multiplier,
				payout_amount,
				created_at
			)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			""",
			predictionId,
			auctionResultId,
			resultStatus,
			errorRate,
			1,
			payoutAmount,
			createdAt
		);
	}

	private Long insertMember(String nicknamePrefix) {
		String nickname = nicknamePrefix + "-" + suffix();
		return insert("""
			INSERT INTO member (nickname, normalized_nickname)
			VALUES (?, ?)
			RETURNING member_id
			""", nickname, nickname);
	}

	private int snapshotRowCount() {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM prediction_leaderboard
			WHERE ranking_date = ?
			""", Integer.class, RANKING_DATE);
	}

	private int distinctSnapshotMemberCount() {
		return jdbcTemplate.queryForObject("""
			SELECT COUNT(DISTINCT member_id)
			FROM prediction_leaderboard
			WHERE ranking_date = ?
			""", Integer.class, RANKING_DATE);
	}

	private Long insert(String sql, Object... arguments) {
		return jdbcTemplate.queryForObject(sql, Long.class, arguments);
	}

	private String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private record FixtureContext(
		Long sellerId,
		Long categoryId
	) {
	}
}
