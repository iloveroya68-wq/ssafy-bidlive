package com.ssafy.bidlive.wallet.infrastructure.persistence;

import static com.ssafy.bidlive.wallet.domain.BettingPointSettlementType.REFUND;
import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.bidlive.wallet.application.BettingPointSettlementCommand;
import com.ssafy.bidlive.wallet.application.BettingPointSettlementResult;
import com.ssafy.bidlive.wallet.application.BettingPointSettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 베팅 지갑 갱신과 정산 원장이 함께 저장되는지 검증한다. */
@SpringBootTest
@Transactional
class BettingPointSettlementPersistenceIntegrationTest {

	private static final String IDEMPOTENCY_KEY = "PREDICTION_SETTLEMENT:701";

	@Autowired
	private BettingPointSettlementService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void creditsRefundAndKeepsOneLedgerForDuplicateRequest() {
		SettlementFixture fixture = createFixture();
		BettingPointSettlementCommand command =
			new BettingPointSettlementCommand(
				fixture.memberId(),
				fixture.predictionId(),
				fixture.predictionResultId(),
				REFUND,
				300L,
				IDEMPOTENCY_KEY
			);

		BettingPointSettlementResult first = service.settle(command);
		BettingPointSettlementResult duplicated = service.settle(command);

		assertThat(first.duplicated()).isFalse();
		assertThat(first.availableBalanceAfter()).isEqualTo(2_300L);
		assertThat(duplicated.duplicated()).isTrue();
		assertThat(duplicated.ledgerId()).isEqualTo(first.ledgerId());
		assertThat(walletAvailableBalance(fixture.walletId()))
			.isEqualTo(2_300L);
		assertThat(ledgerCount()).isEqualTo(1);
		assertThat(ledger(fixture.walletId()))
			.containsExactly(
				"REFUND",
				300L,
				0L,
				2_300L,
				0L,
				fixture.predictionId(),
				fixture.predictionResultId()
			);
	}

	private SettlementFixture createFixture() {
		Long memberId = insert("""
			INSERT INTO member (nickname, normalized_nickname)
			VALUES ('wallet-settlement-member', 'wallet-settlement-member')
			RETURNING member_id
			""");
		Long walletId = insert("""
			INSERT INTO wallet (
				member_id,
				wallet_type,
				available_balance,
				held_balance
			)
			VALUES (?, 'BETTING', 2000, 0)
			RETURNING wallet_id
			""", memberId);
		Long categoryId = insert("""
			INSERT INTO product_category (code, name)
			VALUES ('WALLET_TEST', 'Wallet Test')
			RETURNING category_id
			""");
		Long productId = insert("""
			INSERT INTO product (
				seller_member_id,
				category_id,
				title,
				description
			)
			VALUES (?, ?, 'Settlement product', 'Settlement test product')
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
			VALUES (?, 1000, 10, 'IMMEDIATE', 'ENDED')
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
			VALUES (?, 1000, 300, 'wallet-settlement-prediction')
			RETURNING prediction_id
			""", participantId);
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
		Long predictionResultId = insert("""
			INSERT INTO prediction_result (
				prediction_id,
				auction_result_id,
				result_status,
				error_rate,
				reward_multiplier,
				payout_amount
			)
			VALUES (?, ?, 'REFUNDED', NULL, 1, 300)
			RETURNING prediction_result_id
			""", predictionId, auctionResultId);
		return new SettlementFixture(
			memberId,
			walletId,
			predictionId,
			predictionResultId
		);
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
				resultSet.getLong("prediction_result_id")
			},
			walletId
		);
	}

	private record SettlementFixture(
		Long memberId,
		Long walletId,
		Long predictionId,
		Long predictionResultId
	) {
	}
}
