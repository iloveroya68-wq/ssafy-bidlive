package com.ssafy.bidlive.prediction.application;

import static com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode.INSUFFICIENT_BETTING_POINTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.ssafy.bidlive.wallet.application.BettingWalletRealtimeInitializationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL에서 prediction 저장과 Wallet BET 차감의 동일 트랜잭션을 검증한다. */
@SpringBootTest
class PredictionCommandServiceIntegrationTest {

	private static final OffsetDateTime SUBMITTED_AT =
		OffsetDateTime.parse("2026-07-21T20:08:00+09:00");

	@Autowired
	private PredictionCommandService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private PredictionParticipationProcessor participationProcessor;

	@MockitoBean
	private BettingWalletRealtimeInitializationService
		bettingWalletRealtimeInitializationService;

	@Test
	@Transactional
	void storesPredictionAndBetLedgerOnlyOnceForIdempotentRequest() {
		Fixture fixture = createFixture(2_000L);
		UUID clientRequestId = UUID.fromString(
			"2b8bb687-6d5e-4e09-b651-b88727266a51"
		);
		when(participationProcessor.process(any()))
			.thenReturn(accepted(1_700L));

		PredictionCreateResult first = service.createPrediction(
			fixture.auctionId(),
			fixture.predictorId(),
			74_000L,
			300L,
			clientRequestId
		);
		PredictionCreateResult duplicated = service.createPrediction(
			fixture.auctionId(),
			fixture.predictorId(),
			74_000L,
			300L,
			clientRequestId
		);

		assertThat(duplicated.predictionId()).isEqualTo(first.predictionId());
		assertThat(participantCount(fixture.auctionId())).isEqualTo(1);
		assertThat(predictionCount(clientRequestId)).isEqualTo(1);
		assertThat(prediction(first.predictionId())).containsExactly(
			fixture.auctionId(),
			fixture.predictorId(),
			74_000L,
			300L,
			clientRequestId.toString()
		);
		assertThat(walletBalance(fixture.walletId())).isEqualTo(1_700L);
		assertThat(ledger(first.predictionId())).containsExactly(
			"BET",
			-300L,
			0L,
			1_700L,
			0L,
			first.predictionId(),
			null
		);
		assertThat(ledgerCount(first.predictionId())).isEqualTo(1);
		verify(participationProcessor, times(1)).process(any());
	}

	@Test
	void rollsBackPredictionWhenWalletBalanceIsInsufficient() {
		Fixture fixture = createFixture(299L);
		UUID clientRequestId = UUID.fromString(
			"4a15d22a-58c5-444e-a0fc-346cab264ced"
		);
		when(participationProcessor.process(any()))
			.thenReturn(accepted(0L));

		assertThatThrownBy(() -> service.createPrediction(
			fixture.auctionId(),
			fixture.predictorId(),
			74_000L,
			300L,
			clientRequestId
		)).isInstanceOfSatisfying(
			PredictionRejectedException.class,
			exception -> assertThat(exception.getFailureCode())
				.isEqualTo(INSUFFICIENT_BETTING_POINTS)
		);

		assertThat(participantCount(fixture.auctionId())).isZero();
		assertThat(predictionCount(clientRequestId)).isZero();
		assertThat(walletBalance(fixture.walletId())).isEqualTo(299L);
		assertThat(betLedgerCountByMember(fixture.predictorId())).isZero();
	}

	private PredictionParticipationResult accepted(long availableBalance) {
		return PredictionParticipationResult.accepted(
			SUBMITTED_AT,
			1L,
			availableBalance,
			false
		);
	}

	private Fixture createFixture(long availableBalance) {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Long sellerId = insertMember("pred01-seller-" + suffix);
		Long predictorId = insertMember("pred01-predictor-" + suffix);
		Long walletId = insert("""
			INSERT INTO wallet (
				member_id,
				wallet_type,
				available_balance,
				held_balance
			)
			VALUES (?, 'BETTING', ?, 0)
			RETURNING wallet_id
			""", predictorId, availableBalance);
		Long categoryId = insert("""
			INSERT INTO product_category (code, name)
			VALUES (?, ?)
			RETURNING category_id
			""", "PRED01_" + suffix, "PRED-01 " + suffix);
		Long productId = insert("""
			INSERT INTO product (
				seller_member_id,
				category_id,
				title,
				description
			)
			VALUES (?, ?, 'Prediction product', 'PRED-01 integration test')
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
			VALUES (?, 1000, 10, 'IMMEDIATE', 'LIVE')
			RETURNING auction_room_id
			""", productId);
		return new Fixture(auctionId, predictorId, walletId);
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

	private int participantCount(Long auctionId) {
		return count("""
			SELECT COUNT(*)
			FROM auction_participant
			WHERE auction_room_id = ?
				AND participation_type = 'PREDICTOR'
			""", auctionId);
	}

	private int predictionCount(UUID clientRequestId) {
		return count("""
			SELECT COUNT(*)
			FROM prediction
			WHERE idempotency_key = ?
			""", clientRequestId.toString());
	}

	private int ledgerCount(Long predictionId) {
		return count("""
			SELECT COUNT(*)
			FROM betting_point_ledger
			WHERE prediction_id = ?
			""", predictionId);
	}

	private int betLedgerCountByMember(Long memberId) {
		return count("""
			SELECT COUNT(*)
			FROM betting_point_ledger ledger
			JOIN wallet ON wallet.wallet_id = ledger.wallet_id
			WHERE wallet.member_id = ?
				AND ledger.transaction_type = 'BET'
			""", memberId);
	}

	private int count(String sql, Object... arguments) {
		Integer count = jdbcTemplate.queryForObject(
			sql,
			Integer.class,
			arguments
		);
		return count == null ? 0 : count;
	}

	private Object[] prediction(Long predictionId) {
		return jdbcTemplate.queryForObject(
			"""
				SELECT
					participant.auction_room_id,
					participant.member_id,
					prediction.predicted_price,
					prediction.stake_amount,
					prediction.idempotency_key
				FROM prediction
				JOIN auction_participant participant
					ON participant.auction_participant_id =
						prediction.auction_participant_id
				WHERE prediction.prediction_id = ?
				""",
			(resultSet, rowNumber) -> new Object[] {
				resultSet.getLong("auction_room_id"),
				resultSet.getLong("member_id"),
				resultSet.getLong("predicted_price"),
				resultSet.getLong("stake_amount"),
				resultSet.getString("idempotency_key")
			},
			predictionId
		);
	}

	private long walletBalance(Long walletId) {
		Long balance = jdbcTemplate.queryForObject(
			"""
				SELECT available_balance
				FROM wallet
				WHERE wallet_id = ?
				""",
			Long.class,
			walletId
		);
		return balance == null ? 0L : balance;
	}

	private Object[] ledger(Long predictionId) {
		return jdbcTemplate.queryForObject(
			"""
				SELECT
					transaction_type,
					available_delta,
					held_delta,
					available_balance_after,
					held_balance_after,
					prediction_id,
					prediction_result_id
				FROM betting_point_ledger
				WHERE prediction_id = ?
				""",
			(resultSet, rowNumber) -> new Object[] {
				resultSet.getString("transaction_type"),
				resultSet.getLong("available_delta"),
				resultSet.getLong("held_delta"),
				resultSet.getLong("available_balance_after"),
				resultSet.getLong("held_balance_after"),
				resultSet.getLong("prediction_id"),
				resultSet.getObject("prediction_result_id", Long.class)
			},
			predictionId
		);
	}

	private record Fixture(
		Long auctionId,
		Long predictorId,
		Long walletId
	) {
	}
}
