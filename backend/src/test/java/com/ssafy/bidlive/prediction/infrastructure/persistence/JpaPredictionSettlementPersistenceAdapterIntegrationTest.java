package com.ssafy.bidlive.prediction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionSettlementPersistence;
import com.ssafy.bidlive.prediction.application.PredictionSettlementPersistence.NewSettlement;
import com.ssafy.bidlive.prediction.application.PredictionSettlementPersistence.SaveResult;
import com.ssafy.bidlive.prediction.application.PredictionSettlementPersistence.SettlementTarget;
import com.ssafy.bidlive.prediction.domain.PredictionResultStatus;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementBand;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementDecision;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL에서 PRED-03 정산 대상 조회와 결과의 멱등 저장을 검증한다. */
@SpringBootTest
@Transactional
class JpaPredictionSettlementPersistenceAdapterIntegrationTest {

	@Autowired
	private PredictionSettlementPersistence persistence;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final PredictionSettlementPolicy policy =
		new PredictionSettlementPolicy();

	@Test
	void findsOnlyPredictionsWithoutSettlementResult() {
		Fixture fixture = createFixture(10_000L, 10_100L, 10_400L);
		PredictionFixture settled = fixture.predictions().getFirst();
		persistence.saveIfAbsent(new NewSettlement(
			settled.predictionId(),
			fixture.auctionResultId(),
			policy.settle(
				settled.predictedPrice(),
				fixture.finalPrice(),
				settled.stakeAmount()
			)
		));

		List<SettlementTarget> targets = persistence.findUnsettledTargets(
			fixture.auctionId()
		);

		PredictionFixture expected = fixture.predictions().get(1);
		assertThat(targets).containsExactly(new SettlementTarget(
			expected.predictionId(),
			expected.memberId(),
			expected.predictedPrice(),
			expected.stakeAmount()
		));
	}

	@Test
	void savesSettlementOnlyOnceForSamePrediction() {
		Fixture fixture = createFixture(10_000L, 10_100L);
		PredictionFixture prediction = fixture.predictions().getFirst();
		NewSettlement settlement = new NewSettlement(
			prediction.predictionId(),
			fixture.auctionResultId(),
			policy.settle(
				prediction.predictedPrice(),
				fixture.finalPrice(),
				prediction.stakeAmount()
			)
		);

		SaveResult first = persistence.saveIfAbsent(settlement);
		SaveResult duplicated = persistence.saveIfAbsent(settlement);

		assertThat(first.created()).isTrue();
		assertThat(duplicated.created()).isFalse();
		assertThat(duplicated.settlement().predictionResultId())
			.isEqualTo(first.settlement().predictionResultId());
		assertThat(resultCount(prediction.predictionId())).isEqualTo(1);
		assertThat(first.settlement().decision())
			.isEqualTo(new PredictionSettlementDecision(
				PredictionResultStatus.HIT,
				PredictionSettlementBand.WITHIN_5,
				new BigDecimal("1.000000"),
				5,
				1_500L
			));
	}

	@Test
	void storesErrorRateAboveOneHundredPercent() {
		Fixture fixture = createFixture(100L, 300L);
		PredictionFixture prediction = fixture.predictions().getFirst();

		SaveResult saved = persistence.saveIfAbsent(new NewSettlement(
			prediction.predictionId(),
			fixture.auctionResultId(),
			policy.settle(
				prediction.predictedPrice(),
				fixture.finalPrice(),
				prediction.stakeAmount()
			)
		));

		assertThat(saved.created()).isTrue();
		assertThat(saved.settlement().decision().resultStatus())
			.isEqualTo(PredictionResultStatus.MISS);
		assertThat(saved.settlement().decision().errorRate())
			.isEqualByComparingTo("200.000000");
		assertThat(storedErrorRate(prediction.predictionId()))
			.isEqualByComparingTo("200.000000");
	}

	private Fixture createFixture(
		long finalPrice,
		long... predictedPrices
	) {
		Long sellerId = insertMember("settlement-seller");
		Long bidderId = insertMember("settlement-bidder");
		Long categoryId = insert("""
			INSERT INTO product_category (code, name)
			VALUES ('SETTLEMENT_TEST', 'Settlement Test')
			RETURNING category_id
			""");
		Long productId = insert("""
			INSERT INTO product (
				seller_member_id,
				category_id,
				title,
				description
			)
			VALUES (?, ?, 'Settlement product', 'Prediction settlement test')
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
			VALUES (?, 1, 10, 'IMMEDIATE', 'ENDED')
			RETURNING auction_room_id
			""", productId);
		Long bidderParticipantId = insert("""
			INSERT INTO auction_participant (
				auction_room_id,
				member_id,
				participation_type
			)
			VALUES (?, ?, 'BIDDER')
			RETURNING auction_participant_id
			""", auctionId, bidderId);
		Long winningBidId = insert("""
			INSERT INTO bid (
				auction_participant_id,
				amount,
				sequence_no,
				idempotency_key
			)
			VALUES (?, ?, 1, 'settlement-winning-bid')
			RETURNING bid_id
			""", bidderParticipantId, finalPrice);
		Long auctionResultId = insert("""
			INSERT INTO auction_result (
				auction_room_id,
				result_status,
				termination_reason,
				winner_participant_id,
				winning_bid_id,
				highest_bid_amount,
				final_price,
				valid_bidder_count,
				total_bid_count
			)
			VALUES (?, 'SOLD', 'NORMAL', ?, ?, ?, ?, 1, 1)
			RETURNING auction_result_id
			""",
			auctionId,
			bidderParticipantId,
			winningBidId,
			finalPrice,
			finalPrice
		);

		List<PredictionFixture> predictions = new ArrayList<>();
		for (int index = 0; index < predictedPrices.length; index++) {
			predictions.add(insertPrediction(
				auctionId,
				index,
				predictedPrices[index]
			));
		}
		return new Fixture(
			auctionId,
			auctionResultId,
			finalPrice,
			List.copyOf(predictions)
		);
	}

	private PredictionFixture insertPrediction(
		Long auctionId,
		int index,
		long predictedPrice
	) {
		Long memberId = insertMember("settlement-predictor-" + index);
		Long participantId = insert("""
			INSERT INTO auction_participant (
				auction_room_id,
				member_id,
				participation_type
			)
			VALUES (?, ?, 'PREDICTOR')
			RETURNING auction_participant_id
			""", auctionId, memberId);
		long stakeAmount = 300L;
		Long predictionId = insert("""
			INSERT INTO prediction (
				auction_participant_id,
				predicted_price,
				stake_amount,
				idempotency_key
			)
			VALUES (?, ?, ?, ?)
			RETURNING prediction_id
			""",
			participantId,
			predictedPrice,
			stakeAmount,
			"settlement-prediction-" + index
		);
		return new PredictionFixture(
			predictionId,
			memberId,
			predictedPrice,
			stakeAmount
		);
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

	private int resultCount(Long predictionId) {
		Integer count = jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM prediction_result
				WHERE prediction_id = ?
				""",
			Integer.class,
			predictionId
		);
		return count == null ? 0 : count;
	}

	private BigDecimal storedErrorRate(Long predictionId) {
		return jdbcTemplate.queryForObject(
			"""
				SELECT error_rate
				FROM prediction_result
				WHERE prediction_id = ?
				""",
			BigDecimal.class,
			predictionId
		);
	}

	private record Fixture(
		Long auctionId,
		Long auctionResultId,
		long finalPrice,
		List<PredictionFixture> predictions
	) {
	}

	private record PredictionFixture(
		Long predictionId,
		Long memberId,
		long predictedPrice,
		long stakeAmount
	) {
	}
}
