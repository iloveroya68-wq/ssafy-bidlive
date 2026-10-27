package com.ssafy.bidlive.prediction.application;

import static com.ssafy.bidlive.prediction.application.PredictionSettlementCommand.CompletionType.FORCED;
import static com.ssafy.bidlive.prediction.application.PredictionSettlementCommand.CompletionType.SOLD;
import static com.ssafy.bidlive.wallet.application.BettingPointSettlementErrorCode.BETTING_WALLET_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ssafy.bidlive.prediction.api.dto.PredictionSettledMessage;
import com.ssafy.bidlive.wallet.application.BettingPointSettlementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 DB 정산 트랜잭션과 커밋 후 완료 이벤트 발행을 함께 검증한다. */
@SpringBootTest
class PredictionSettlementServiceIntegrationTest {

	private static final long INITIAL_BALANCE = 2_000L;
	private static final long STAKE_AMOUNT = 300L;

	@Autowired
	private PredictionSettlementService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean
	private SimpMessagingTemplate messagingTemplate;

	@Test
	@Transactional
	void settlesHitAndDoesNotPayAgainOnReprocessing() {
		Fixture fixture = createFixture(
			10_100L,
			10_000L,
			"NORMAL",
			true
		);
		PredictionSettlementCommand command =
			new PredictionSettlementCommand(
				fixture.auctionId(),
				fixture.auctionResultId(),
				fixture.finalPrice(),
				SOLD
			);

		PredictionSettlementResult first = service.settle(command);
		PredictionSettlementResult reprocessed = service.settle(command);

		assertThat(first).isEqualTo(new PredictionSettlementResult(
			fixture.auctionId(),
			1,
			1,
			0,
			STAKE_AMOUNT,
			1_500L
		));
		assertThat(reprocessed).isEqualTo(new PredictionSettlementResult(
			fixture.auctionId(),
			0,
			0,
			0,
			0L,
			0L
		));
		assertThat(resultRow(fixture.predictionId()))
			.isEqualTo(new PredictionResultRow(
				"HIT",
				new BigDecimal("1.000000"),
				new BigDecimal("5.00"),
				1_500L
			));
		assertThat(walletBalance(fixture.predictorMemberId()))
			.isEqualTo(3_500L);
		assertThat(ledgerRows(fixture.predictionId()))
			.containsExactly(new LedgerRow("PAYOUT", 1_500L, 3_500L));
		assertThat(achievementRow(fixture.predictorMemberId()))
			.isEqualTo(new AchievementRow("ORACLE_1", 100L));
		assertThat(badgeCodes(fixture.predictorMemberId()))
			.containsExactly("FIRST_HIT", "PINPOINT");
	}

	@Test
	@Transactional
	void persistsMissWithoutChangingWallet() {
		Fixture fixture = createFixture(
			10_501L,
			10_000L,
			"NORMAL",
			true
		);

		PredictionSettlementResult result = service.settle(
			new PredictionSettlementCommand(
				fixture.auctionId(),
				fixture.auctionResultId(),
				fixture.finalPrice(),
				SOLD
			)
		);

		assertThat(result.totalPayoutPoints()).isZero();
		assertThat(resultRow(fixture.predictionId()))
			.isEqualTo(new PredictionResultRow(
				"MISS",
				new BigDecimal("5.010000"),
				new BigDecimal("0.00"),
				0L
			));
		assertThat(walletBalance(fixture.predictorMemberId()))
			.isEqualTo(INITIAL_BALANCE);
		assertThat(ledgerRows(fixture.predictionId())).isEmpty();
		assertThat(achievementRow(fixture.predictorMemberId()))
			.isEqualTo(new AchievementRow("ORACLE_1", 0L));
		assertThat(badgeCodes(fixture.predictorMemberId()))
			.isEmpty();
	}

	@Test
	@Transactional
	void refundsFullStakeForForcedTermination() {
		Fixture fixture = createFixture(
			20_000L,
			null,
			"FORCED",
			true
		);

		PredictionSettlementResult result = service.settle(
			new PredictionSettlementCommand(
				fixture.auctionId(),
				fixture.auctionResultId(),
				null,
				FORCED
			)
		);

		assertThat(result.totalPayoutPoints()).isEqualTo(STAKE_AMOUNT);
		assertThat(resultRow(fixture.predictionId()))
			.isEqualTo(new PredictionResultRow(
				"REFUNDED",
				null,
				new BigDecimal("1.00"),
				STAKE_AMOUNT
			));
		assertThat(walletBalance(fixture.predictorMemberId()))
			.isEqualTo(2_300L);
		assertThat(ledgerRows(fixture.predictionId()))
			.containsExactly(new LedgerRow("REFUND", 300L, 2_300L));
		assertThat(achievementCount(fixture.predictorMemberId()))
			.isZero();
	}

	@Test
	void broadcastsCompletionOnlyAfterTransactionCommit() {
		TransactionTemplate transaction = new TransactionTemplate(
			transactionManager
		);
		Fixture fixture = transaction.execute(status -> createFixture(
			10_100L,
			10_000L,
			"NORMAL",
			true
		));
		reset(messagingTemplate);

		try {
			transaction.executeWithoutResult(status -> {
				service.settle(
					new PredictionSettlementCommand(
						fixture.auctionId(),
						fixture.auctionResultId(),
						fixture.finalPrice(),
						SOLD
					)
				);
				verify(messagingTemplate, never()).convertAndSend(
					"/topic/auctions/" + fixture.auctionId(),
					PredictionSettledMessage.completed()
				);
			});
			verify(messagingTemplate).convertAndSend(
				"/topic/auctions/" + fixture.auctionId(),
				PredictionSettledMessage.completed()
			);
		} finally {
			transaction.executeWithoutResult(status -> cleanup(fixture));
		}
	}

	@Test
	void rollsBackPredictionResultWhenBettingWalletIsMissing() {
		TransactionTemplate transaction = new TransactionTemplate(
			transactionManager
		);
		Fixture fixture = transaction.execute(status -> createFixture(
			10_100L,
			10_000L,
			"NORMAL",
			false
		));
		reset(messagingTemplate);

		try {
			assertThatThrownBy(() -> service.settle(
				new PredictionSettlementCommand(
					fixture.auctionId(),
					fixture.auctionResultId(),
					fixture.finalPrice(),
					SOLD
				)
			)).isInstanceOfSatisfying(
				BettingPointSettlementException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(BETTING_WALLET_NOT_FOUND)
			);

			assertThat(resultCount(fixture.predictionId())).isZero();
			assertThat(ledgerRows(fixture.predictionId())).isEmpty();
			assertThat(achievementCount(fixture.predictorMemberId()))
				.isZero();
			verify(messagingTemplate, never()).convertAndSend(
				"/topic/auctions/" + fixture.auctionId(),
				PredictionSettledMessage.completed()
			);
		} finally {
			transaction.executeWithoutResult(status -> cleanup(fixture));
		}
	}

	private Fixture createFixture(
		long predictedPrice,
		Long finalPrice,
		String terminationReason,
		boolean createWallet
	) {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		List<Long> memberIds = new ArrayList<>();
		Long sellerId = insertMember("ps-seller-" + suffix);
		memberIds.add(sellerId);
		Long predictorId = insertMember("ps-predictor-" + suffix);
		memberIds.add(predictorId);

		Long categoryId = insert("""
			INSERT INTO product_category (code, name)
			VALUES (?, 'Prediction Settlement')
			RETURNING category_id
			""", "PS_" + suffix);
		Long productId = insert("""
			INSERT INTO product (
				seller_member_id,
				category_id,
				title,
				description
			)
			VALUES (?, ?, 'Settlement product', 'Service integration test')
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

		Long auctionResultId;
		if (finalPrice == null) {
			auctionResultId = insertRefundedAuctionResult(
				auctionId,
				terminationReason
			);
		} else {
			Long bidderId = insertMember("ps-bidder-" + suffix);
			memberIds.add(bidderId);
			auctionResultId = insertSoldAuctionResult(
				auctionId,
				bidderId,
				finalPrice,
				suffix
			);
		}

		Long predictorParticipantId = insert("""
			INSERT INTO auction_participant (
				auction_room_id,
				member_id,
				participation_type
			)
			VALUES (?, ?, 'PREDICTOR')
			RETURNING auction_participant_id
			""", auctionId, predictorId);
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
			predictorParticipantId,
			predictedPrice,
			STAKE_AMOUNT,
			"prediction-settlement-" + suffix
		);
		if (createWallet) {
			insert("""
				INSERT INTO wallet (
					member_id,
					wallet_type,
					available_balance,
					held_balance
				)
				VALUES (?, 'BETTING', ?, 0)
				RETURNING wallet_id
				""", predictorId, INITIAL_BALANCE);
		}

		return new Fixture(
			auctionId,
			auctionResultId,
			productId,
			categoryId,
			predictionId,
			predictorId,
			finalPrice,
			List.copyOf(memberIds)
		);
	}

	private Long insertSoldAuctionResult(
		Long auctionId,
		Long bidderId,
		long finalPrice,
		String suffix
	) {
		Long participantId = insert("""
			INSERT INTO auction_participant (
				auction_room_id,
				member_id,
				participation_type
			)
			VALUES (?, ?, 'BIDDER')
			RETURNING auction_participant_id
			""", auctionId, bidderId);
		Long bidId = insert("""
			INSERT INTO bid (
				auction_participant_id,
				amount,
				sequence_no,
				idempotency_key
			)
			VALUES (?, ?, 1, ?)
			RETURNING bid_id
			""", participantId, finalPrice, "winning-bid-" + suffix);
		return insert("""
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
			participantId,
			bidId,
			finalPrice,
			finalPrice
		);
	}

	private Long insertRefundedAuctionResult(
		Long auctionId,
		String terminationReason
	) {
		return insert("""
			INSERT INTO auction_result (
				auction_room_id,
				result_status,
				termination_reason,
				valid_bidder_count,
				total_bid_count
			)
			VALUES (?, 'NO_SALE', ?, 0, 0)
			RETURNING auction_result_id
			""", auctionId, terminationReason);
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

	private PredictionResultRow resultRow(Long predictionId) {
		return jdbcTemplate.queryForObject(
			"""
				SELECT
					result_status,
					error_rate,
					reward_multiplier,
					payout_amount
				FROM prediction_result
				WHERE prediction_id = ?
				""",
			(resultSet, rowNumber) -> new PredictionResultRow(
				resultSet.getString("result_status"),
				resultSet.getBigDecimal("error_rate"),
				resultSet.getBigDecimal("reward_multiplier"),
				resultSet.getLong("payout_amount")
			),
			predictionId
		);
	}

	private long walletBalance(Long memberId) {
		Long balance = jdbcTemplate.queryForObject(
			"""
				SELECT available_balance
				FROM wallet
				WHERE member_id = ?
					AND wallet_type = 'BETTING'
				""",
			Long.class,
			memberId
		);
		return balance == null ? 0L : balance;
	}

	private List<LedgerRow> ledgerRows(Long predictionId) {
		return jdbcTemplate.query(
			"""
				SELECT
					transaction_type,
					available_delta,
					available_balance_after
				FROM betting_point_ledger
				WHERE prediction_id = ?
				ORDER BY betting_point_ledger_id
				""",
			(resultSet, rowNumber) -> new LedgerRow(
				resultSet.getString("transaction_type"),
				resultSet.getLong("available_delta"),
				resultSet.getLong("available_balance_after")
			),
			predictionId
		);
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

	private AchievementRow achievementRow(Long memberId) {
		return jdbcTemplate.queryForObject(
			"""
				SELECT grade_code, grade_score
				FROM prediction_achievement
				WHERE member_id = ?
				""",
			(resultSet, rowNumber) -> new AchievementRow(
				resultSet.getString("grade_code"),
				resultSet.getLong("grade_score")
			),
			memberId
		);
	}

	private List<String> badgeCodes(Long memberId) {
		return jdbcTemplate.queryForList(
			"""
				SELECT badge_code
				FROM prediction_badge
				WHERE member_id = ?
				ORDER BY badge_code
				""",
			String.class,
			memberId
		);
	}

	private int achievementCount(Long memberId) {
		Integer count = jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM prediction_achievement
				WHERE member_id = ?
				""",
			Integer.class,
			memberId
		);
		return count == null ? 0 : count;
	}

	private void cleanup(Fixture fixture) {
		jdbcTemplate.update(
			"DELETE FROM prediction_badge WHERE member_id = ?",
			fixture.predictorMemberId()
		);
		jdbcTemplate.update(
			"DELETE FROM prediction_achievement WHERE member_id = ?",
			fixture.predictorMemberId()
		);
		jdbcTemplate.update(
			"DELETE FROM betting_point_ledger WHERE prediction_id = ?",
			fixture.predictionId()
		);
		jdbcTemplate.update(
			"DELETE FROM prediction_result WHERE prediction_id = ?",
			fixture.predictionId()
		);
		jdbcTemplate.update(
			"DELETE FROM prediction WHERE prediction_id = ?",
			fixture.predictionId()
		);
		jdbcTemplate.update(
			"DELETE FROM auction_result WHERE auction_result_id = ?",
			fixture.auctionResultId()
		);
		jdbcTemplate.update(
			"""
				DELETE FROM bid
				WHERE auction_participant_id IN (
					SELECT auction_participant_id
					FROM auction_participant
					WHERE auction_room_id = ?
				)
				""",
			fixture.auctionId()
		);
		jdbcTemplate.update(
			"DELETE FROM wallet WHERE member_id = ?",
			fixture.predictorMemberId()
		);
		jdbcTemplate.update(
			"DELETE FROM auction_participant WHERE auction_room_id = ?",
			fixture.auctionId()
		);
		jdbcTemplate.update(
			"DELETE FROM auction_room WHERE auction_room_id = ?",
			fixture.auctionId()
		);
		jdbcTemplate.update(
			"DELETE FROM product WHERE product_id = ?",
			fixture.productId()
		);
		jdbcTemplate.update(
			"DELETE FROM product_category WHERE category_id = ?",
			fixture.categoryId()
		);
		for (Long memberId : fixture.memberIds()) {
			jdbcTemplate.update(
				"DELETE FROM member WHERE member_id = ?",
				memberId
			);
		}
	}

	private record PredictionResultRow(
		String status,
		BigDecimal errorRate,
		BigDecimal multiplier,
		long payoutAmount
	) {
	}

	private record AchievementRow(
		String gradeCode,
		long gradeScore
	) {
	}

	private record LedgerRow(
		String transactionType,
		long availableDelta,
		long availableBalanceAfter
	) {
	}

	private record Fixture(
		Long auctionId,
		Long auctionResultId,
		Long productId,
		Long categoryId,
		Long predictionId,
		Long predictorMemberId,
		Long finalPrice,
		List<Long> memberIds
	) {
	}
}
