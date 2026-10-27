package com.ssafy.bidlive.wallet.infrastructure.persistence;

import static com.ssafy.bidlive.wallet.application.BettingPointBetErrorCode.INSUFFICIENT_BETTING_POINTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.bidlive.wallet.application.BettingPointBetCommand;
import com.ssafy.bidlive.wallet.application.BettingPointBetException;
import com.ssafy.bidlive.wallet.application.BettingPointBetResult;
import com.ssafy.bidlive.wallet.application.BettingPointBetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 BET 차감과 원장이 함께 저장되는지 검증한다. */
@SpringBootTest
@Transactional
class BettingPointBetPersistenceIntegrationTest {

	private static final String IDEMPOTENCY_KEY = "PREDICTION_BET:request-701";

	@Autowired
	private BettingPointBetService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void deductsStakeAndKeepsOneLedgerForDuplicateRequest() {
		BetFixture fixture = createFixture(2_000L);
		BettingPointBetCommand command = new BettingPointBetCommand(
			fixture.memberId(),
			fixture.predictionId(),
			300L,
			IDEMPOTENCY_KEY
		);

		BettingPointBetResult first = service.deduct(command);
		BettingPointBetResult duplicated = service.deduct(command);

		assertThat(first.duplicated()).isFalse();
		assertThat(first.availableBalanceAfter()).isEqualTo(1_700L);
		assertThat(duplicated.duplicated()).isTrue();
		assertThat(duplicated.ledgerId()).isEqualTo(first.ledgerId());
		assertThat(walletAvailableBalance(fixture.walletId()))
			.isEqualTo(1_700L);
		assertThat(ledgerCount()).isEqualTo(1);
		assertThat(ledger(fixture.walletId()))
			.containsExactly(
				"BET",
				-300L,
				0L,
				1_700L,
				0L,
				fixture.predictionId(),
				null
			);
	}

	@Test
	void leavesWalletAndLedgerUnchangedWhenBalanceIsInsufficient() {
		BetFixture fixture = createFixture(299L);
		BettingPointBetCommand command = new BettingPointBetCommand(
			fixture.memberId(),
			fixture.predictionId(),
			300L,
			IDEMPOTENCY_KEY
		);

		assertThatThrownBy(() -> service.deduct(command))
			.isInstanceOfSatisfying(
				BettingPointBetException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(INSUFFICIENT_BETTING_POINTS)
			);

		assertThat(walletAvailableBalance(fixture.walletId()))
			.isEqualTo(299L);
		assertThat(ledgerCount()).isZero();
	}

	private BetFixture createFixture(long availableBalance) {
		Long memberId = insert("""
			INSERT INTO member (nickname, normalized_nickname)
			VALUES (?, ?)
			RETURNING member_id
			""",
			"wallet-bet-member-" + availableBalance,
			"wallet-bet-member-" + availableBalance
		);
		Long walletId = insert("""
			INSERT INTO wallet (
				member_id,
				wallet_type,
				available_balance,
				held_balance
			)
			VALUES (?, 'BETTING', ?, 0)
			RETURNING wallet_id
			""", memberId, availableBalance);
		Long categoryId = insert("""
			INSERT INTO product_category (code, name)
			VALUES (?, ?)
			RETURNING category_id
			""",
			"WALLET_BET_" + availableBalance,
			"Wallet Bet " + availableBalance
		);
		Long productId = insert("""
			INSERT INTO product (
				seller_member_id,
				category_id,
				title,
				description
			)
			VALUES (?, ?, 'Bet product', 'Bet test product')
			RETURNING product_id
			""", memberId, categoryId);
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
			VALUES (?, 1000, 300, ?)
			RETURNING prediction_id
			""", participantId, "wallet-bet-prediction-" + availableBalance);
		return new BetFixture(memberId, walletId, predictionId);
	}

	private Long insert(String sql, Object... arguments) {
		return jdbcTemplate.queryForObject(sql, Long.class, arguments);
	}

	private long walletAvailableBalance(Long walletId) {
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

	private int ledgerCount() {
		Integer count = jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM betting_point_ledger
				WHERE idempotency_key = ?
				""",
			Integer.class,
			IDEMPOTENCY_KEY
		);
		return count == null ? 0 : count;
	}

	private Object[] ledger(Long walletId) {
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
				WHERE wallet_id = ?
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
			walletId
		);
	}

	private record BetFixture(
		Long memberId,
		Long walletId,
		Long predictionId
	) {
	}
}
