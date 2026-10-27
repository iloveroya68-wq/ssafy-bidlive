package com.ssafy.bidlive.prediction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import com.ssafy.bidlive.prediction.application.PredictionResultAvailabilityPersistence;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL에서 경매 종료와 예측별 정산 결과 건수를 정확히 조회하는지 검증한다. */
@SpringBootTest
@Transactional
class JpaPredictionResultAvailabilityPersistenceAdapterIntegrationTest {

	@Autowired
	private PredictionResultAvailabilityPersistence persistence;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void returnsEmptyForMissingAuction() {
		assertThat(persistence.findStateByAuctionId(Long.MAX_VALUE))
			.isEmpty();
	}

	@Test
	void findsLiveAuctionWithoutResultAsIncomplete() {
		Fixture fixture = createFixture("LIVE", false, 1, 0);

		assertThat(persistence.findStateByAuctionId(fixture.auctionId()))
			.contains(new PredictionSettlementState(
				null,
				false,
				1L,
				0L
			));
	}

	@Test
	void treatsEndedAuctionWithoutPredictionsAsCompleted() {
		Fixture fixture = createFixture("ENDED", true, 0, 0);

		assertThat(persistence.findStateByAuctionId(fixture.auctionId()))
			.contains(new PredictionSettlementState(
				fixture.auctionResultId(),
				true,
				0L,
				0L
			));
	}

	@Test
	void countsOnlyPredictionsWithSettlementResults() {
		Fixture fixture = createFixture("ENDED", true, 2, 1);

		Optional<PredictionSettlementState> state =
			persistence.findStateByAuctionId(fixture.auctionId());

		assertThat(state).contains(new PredictionSettlementState(
			fixture.auctionResultId(),
			true,
			2L,
			1L
		));
		assertThat(state.orElseThrow().completed()).isFalse();
	}

	private Fixture createFixture(
		String status,
		boolean createResult,
		int predictionCount,
		int settledPredictionCount
	) {
		Long sellerId = insertMember("availability-seller");
		Long categoryId = insert("""
			INSERT INTO product_category (code, name)
			VALUES ('AVAILABILITY_TEST', 'Availability Test')
			RETURNING category_id
			""");
		Long productId = insert("""
			INSERT INTO product (
				seller_member_id,
				category_id,
				title,
				description
			)
			VALUES (?, ?, 'Availability product', 'Availability test')
			RETURNING product_id
			""", sellerId, categoryId);
		Long auctionId = insert("""
			INSERT INTO auction_room (
				product_id,
				reserve_price,
				duration_minutes,
				start_type,
				status
			)
			VALUES (?, 1, 10, 'IMMEDIATE', ?)
			RETURNING auction_room_id
			""", productId, status);
		Long auctionResultId = createResult
			? insertAuctionResult(auctionId)
			: null;

		for (int index = 0; index < predictionCount; index++) {
			Long predictionId = insertPrediction(auctionId, index);
			if (index < settledPredictionCount) {
				insertPredictionResult(predictionId, auctionResultId);
			}
		}
		return new Fixture(auctionId, auctionResultId);
	}

	private Long insertAuctionResult(Long auctionId) {
		return insert("""
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
	}

	private Long insertPrediction(Long auctionId, int index) {
		Long memberId = insertMember("availability-predictor-" + index);
		Long participantId = insert("""
			INSERT INTO auction_participant (
				auction_room_id,
				member_id,
				participation_type
			)
			VALUES (?, ?, 'PREDICTOR')
			RETURNING auction_participant_id
			""", auctionId, memberId);
		return insert("""
			INSERT INTO prediction (
				auction_participant_id,
				predicted_price,
				stake_amount,
				idempotency_key
			)
			VALUES (?, 10000, 300, ?)
			RETURNING prediction_id
			""", participantId, "availability-prediction-" + index);
	}

	private void insertPredictionResult(
		Long predictionId,
		Long auctionResultId
	) {
		jdbcTemplate.update("""
			INSERT INTO prediction_result (
				prediction_id,
				auction_result_id,
				result_status,
				error_rate,
				reward_multiplier,
				payout_amount
			)
			VALUES (?, ?, 'REFUNDED', NULL, 1, 300)
			""", predictionId, auctionResultId);
	}

	private Long insertMember(String nickname) {
		return insert("""
			INSERT INTO member (nickname, normalized_nickname)
			VALUES (?, ?)
			RETURNING member_id
			""", nickname, nickname);
	}

	private Long insert(String sql, Object... arguments) {
		return jdbcTemplate.queryForObject(sql, Long.class, arguments);
	}

	private record Fixture(
		Long auctionId,
		Long auctionResultId
	) {
	}
}
