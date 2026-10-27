package com.ssafy.bidlive.prediction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionResultAggregationPersistence;
import com.ssafy.bidlive.prediction.application.PredictionResultAggregationPersistence.BandAggregate;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementBand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL에서 PRED-02 적중 밴드별 인원과 지급 합계를 검증한다. */
@SpringBootTest
@Transactional
class JpaPredictionResultAggregationPersistenceAdapterIntegrationTest {

	@Autowired
	private PredictionResultAggregationPersistence persistence;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void aggregatesEveryHitBandInDisplayOrderAndFillsEmptyBand() {
		Fixture fixture = createFixture("band-aggregate");
		insertPredictionResult(
			fixture,
			"HIT",
			new BigDecimal("5.00"),
			1_500L
		);
		insertPredictionResult(
			fixture,
			"HIT",
			new BigDecimal("5.00"),
			2_000L
		);
		insertPredictionResult(
			fixture,
			"HIT",
			BigDecimal.ONE,
			300L
		);
		insertPredictionResult(
			fixture,
			"HIT",
			new BigDecimal("3.00"),
			900L
		);
		insertPredictionResult(
			fixture,
			"HIT",
			new BigDecimal("1.50"),
			451L
		);
		insertPredictionResult(
			fixture,
			"MISS",
			BigDecimal.ZERO,
			0L
		);
		insertPredictionResult(
			fixture,
			"MISS",
			BigDecimal.ZERO,
			0L
		);

		List<BandAggregate> aggregates =
			persistence.findBandAggregatesByAuctionResultId(
				fixture.auctionResultId()
			);

		assertThat(aggregates).containsExactly(
			new BandAggregate(
				PredictionSettlementBand.WITHIN_5,
				3L,
				5,
				3_800L
			),
			new BandAggregate(
				PredictionSettlementBand.WITHIN_10,
				1L,
				3,
				900L
			),
			new BandAggregate(
				PredictionSettlementBand.WITHIN_20,
				1L,
				new BigDecimal("1.5"),
				451L
			),
			new BandAggregate(
				PredictionSettlementBand.MISS,
				2L,
				0,
				0L
			)
		);
	}

	@Test
	void excludesOtherAuctionResultsAndRefundsFromHitBands() {
		Fixture target = createFixture("band-target");
		Fixture other = createFixture("band-other");
		insertPredictionResult(
			target,
			"REFUNDED",
			BigDecimal.ONE,
			300L
		);
		insertPredictionResult(
			other,
			"HIT",
			new BigDecimal("5.00"),
			1_500L
		);

		assertThat(persistence.findBandAggregatesByAuctionResultId(
			target.auctionResultId()
		)).containsExactly(
			new BandAggregate(
				PredictionSettlementBand.WITHIN_5,
				0L,
				5,
				0L
			),
			new BandAggregate(
				PredictionSettlementBand.WITHIN_10,
				0L,
				3,
				0L
			),
			new BandAggregate(
				PredictionSettlementBand.WITHIN_20,
				0L,
				new BigDecimal("1.5"),
				0L
			),
			new BandAggregate(
				PredictionSettlementBand.MISS,
				0L,
				0,
				0L
			)
		);
	}

	private Fixture createFixture(String key) {
		Long sellerId = insertMember(key + "-seller");
		Long categoryId = insert("""
			INSERT INTO product_category (code, name)
			VALUES (?, ?)
			RETURNING category_id
			""", key.toUpperCase().replace('-', '_'), key);
		Long productId = insert("""
			INSERT INTO product (
				seller_member_id,
				category_id,
				title,
				description
			)
			VALUES (?, ?, ?, 'Band aggregate test')
			RETURNING product_id
			""", sellerId, categoryId, key);
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
		return new Fixture(auctionId, auctionResultId, key);
	}

	private void insertPredictionResult(
		Fixture fixture,
		String resultStatus,
		BigDecimal multiplier,
		long payoutAmount
	) {
		int sequence = countPredictions(fixture.auctionId());
		Long memberId = insertMember(
			fixture.key() + "-predictor-" + sequence
		);
		Long participantId = insert("""
			INSERT INTO auction_participant (
				auction_room_id,
				member_id,
				participation_type
			)
			VALUES (?, ?, 'PREDICTOR')
			RETURNING auction_participant_id
			""", fixture.auctionId(), memberId);
		Long predictionId = insert("""
			INSERT INTO prediction (
				auction_participant_id,
				predicted_price,
				stake_amount,
				idempotency_key
			)
			VALUES (?, 10000, 300, ?)
			RETURNING prediction_id
			""", participantId, fixture.key() + "-prediction-" + sequence);
		jdbcTemplate.update("""
			INSERT INTO prediction_result (
				prediction_id,
				auction_result_id,
				result_status,
				error_rate,
				reward_multiplier,
				payout_amount
			)
			VALUES (?, ?, ?, ?, ?, ?)
			""",
			predictionId,
			fixture.auctionResultId(),
			resultStatus,
			resultStatus.equals("REFUNDED") ? null : BigDecimal.ZERO,
			multiplier,
			payoutAmount
		);
	}

	private int countPredictions(Long auctionId) {
		Integer count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM prediction prediction
			JOIN auction_participant participant
				ON participant.auction_participant_id =
					prediction.auction_participant_id
			WHERE participant.auction_room_id = ?
			""", Integer.class, auctionId);
		return count == null ? 0 : count;
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
		Long auctionResultId,
		String key
	) {
	}
}
