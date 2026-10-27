package com.ssafy.bidlive.bid.application;

import static com.ssafy.bidlive.bid.domain.BidFailureCode.INSUFFICIENT_AVAILABLE_BALANCE;
import static com.ssafy.bidlive.bid.domain.BidFailureCode.PREDICTION_ALREADY_JOINED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.ssafy.bidlive.wallet.application.TradeWalletRealtimeInitializationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** PostgreSQL에서 입찰 이력과 TRADE 차액 홀드가 함께 커밋·롤백되는지 검증한다. */
@SpringBootTest
class BidCommandServiceIntegrationTest {

	private static final OffsetDateTime FIRST_SUBMITTED_AT =
		OffsetDateTime.parse("2026-07-20T21:12:44+09:00");
	private static final OffsetDateTime SECOND_SUBMITTED_AT =
		FIRST_SUBMITTED_AT.plusSeconds(1);

	@Autowired
	private BidCommandService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private BidProcessor bidProcessor;

	@MockitoBean
	private TradeWalletRealtimeInitializationService
		tradeWalletRealtimeInitializationService;

	@Test
	void storesEveryHigherBidAndHoldsOnlyEachDifference() {
		Fixture fixture = createFixture(200_000L);
		UUID firstRequestId = UUID.randomUUID();
		UUID secondRequestId = UUID.randomUUID();
		when(bidProcessor.process(any()))
			.thenReturn(
				accepted(0L, 70_000L, 70_000L, FIRST_SUBMITTED_AT),
				accepted(70_000L, 82_000L, 12_000L, SECOND_SUBMITTED_AT)
			);

		BidResult first = service.createBid(
			fixture.auctionId(),
			fixture.bidderId(),
			70_000L,
			firstRequestId
		);
		BidResult second = service.createBid(
			fixture.auctionId(),
			fixture.bidderId(),
			82_000L,
			secondRequestId
		);
		BidResult duplicated = service.createBid(
			fixture.auctionId(),
			fixture.bidderId(),
			70_000L,
			firstRequestId
		);

		assertThat(first.previousBidAmount()).isZero();
		assertThat(first.additionalHoldAmount()).isEqualTo(70_000L);
		assertThat(second.previousBidAmount()).isEqualTo(70_000L);
		assertThat(second.additionalHoldAmount()).isEqualTo(12_000L);
		assertThat(second.totalHeldAmount()).isEqualTo(82_000L);
		assertThat(second.availableBalance()).isEqualTo(118_000L);
		assertThat(duplicated.submittedAt()).isEqualTo(FIRST_SUBMITTED_AT);
		assertThat(duplicated.myBidAmount()).isEqualTo(70_000L);
		assertThat(duplicated.totalHeldAmount()).isEqualTo(82_000L);

		assertThat(participantCount(fixture.auctionId())).isEqualTo(1);
		assertThat(bids(fixture.auctionId())).containsExactly(
			new StoredBidRow(1, 70_000L, firstRequestId.toString()),
			new StoredBidRow(2, 82_000L, secondRequestId.toString())
		);
		assertThat(walletBalances(fixture.tradeWalletId()))
			.containsExactly(118_000L, 82_000L);
		assertThat(ledgers(fixture.tradeWalletId())).containsExactly(
			new LedgerRow("HOLD", -70_000L, 70_000L),
			new LedgerRow("HOLD", -12_000L, 12_000L)
		);
		verify(bidProcessor, times(2)).process(any());
	}

	@Test
	void rollsBackParticipantAndBidWhenDatabaseWalletIsInsufficient() {
		Fixture fixture = createFixture(69_999L);
		UUID requestId = UUID.randomUUID();
		when(bidProcessor.process(any())).thenReturn(accepted(
			0L,
			70_000L,
			70_000L,
			FIRST_SUBMITTED_AT
		));

		assertThatThrownBy(() -> service.createBid(
			fixture.auctionId(),
			fixture.bidderId(),
			70_000L,
			requestId
		)).isInstanceOfSatisfying(
			BidRejectedException.class,
			exception -> assertThat(exception.getFailureCode())
				.isEqualTo(INSUFFICIENT_AVAILABLE_BALANCE)
		);

		assertThat(participantCount(fixture.auctionId())).isZero();
		assertThat(bidCount(requestId)).isZero();
		assertThat(walletBalances(fixture.tradeWalletId()))
			.containsExactly(69_999L, 0L);
		assertThat(ledgerCount(fixture.tradeWalletId())).isZero();
	}

	@Test
	void rejectsStoredPredictorBeforeCallingRedis() {
		Fixture fixture = createFixture(200_000L);
		Long participantId = insert("""
			INSERT INTO auction_participant (
				auction_room_id,
				member_id,
				participation_type
			)
			VALUES (?, ?, 'PREDICTOR')
			RETURNING auction_participant_id
			""", fixture.auctionId(), fixture.bidderId());
		jdbcTemplate.update("""
			INSERT INTO prediction (
				auction_participant_id,
				predicted_price,
				stake_amount,
				idempotency_key
			)
			VALUES (?, 74000, 300, ?)
			""", participantId, UUID.randomUUID().toString());

		assertThatThrownBy(() -> service.createBid(
			fixture.auctionId(),
			fixture.bidderId(),
			70_000L,
			UUID.randomUUID()
		)).isInstanceOfSatisfying(
			BidRejectedException.class,
			exception -> assertThat(exception.getFailureCode())
				.isEqualTo(PREDICTION_ALREADY_JOINED)
		);

		verify(bidProcessor, times(0)).process(any());
	}

	private BidProcessingResult accepted(
		long previousAmount,
		long currentAmount,
		long additionalHold,
		OffsetDateTime submittedAt
	) {
		return BidProcessingResult.accepted(
			previousAmount,
			currentAmount,
			additionalHold,
			currentAmount,
			200_000L - currentAmount,
			submittedAt,
			1L,
			1L
		);
	}

	private Fixture createFixture(long availableBalance) {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Long sellerId = insertMember("bid06-seller-" + suffix);
		Long bidderId = insertMember("bid06-bidder-" + suffix);
		Long tradeWalletId = insert("""
			INSERT INTO wallet (
				member_id,
				wallet_type,
				available_balance,
				held_balance
			)
			VALUES (?, 'TRADE', ?, 0)
			RETURNING wallet_id
			""", bidderId, availableBalance);
		insert("""
			INSERT INTO wallet (
				member_id,
				wallet_type,
				available_balance,
				held_balance
			)
			VALUES (?, 'BETTING', 2000, 0)
			RETURNING wallet_id
			""", bidderId);
		Long categoryId = insert("""
			INSERT INTO product_category (code, name)
			VALUES (?, ?)
			RETURNING category_id
			""", "BID06_" + suffix, "BID-06 " + suffix);
		Long productId = insert("""
			INSERT INTO product (
				seller_member_id,
				category_id,
				title,
				description
			)
			VALUES (?, ?, 'Bid product', 'BID-06 integration test')
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
			VALUES (?, 50000, 10, 'IMMEDIATE', 'LIVE')
			RETURNING auction_room_id
			""", productId);
		return new Fixture(auctionId, bidderId, tradeWalletId);
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
				AND participation_type = 'BIDDER'
			""", auctionId);
	}

	private int bidCount(UUID requestId) {
		return count(
			"SELECT COUNT(*) FROM bid WHERE idempotency_key = ?",
			requestId.toString()
		);
	}

	private int ledgerCount(Long walletId) {
		return count(
			"SELECT COUNT(*) FROM trade_point_ledger WHERE wallet_id = ?",
			walletId
		);
	}

	private int count(String sql, Object... arguments) {
		Integer count = jdbcTemplate.queryForObject(
			sql,
			Integer.class,
			arguments
		);
		return count == null ? 0 : count;
	}

	private List<StoredBidRow> bids(Long auctionId) {
		return jdbcTemplate.query("""
			SELECT bid.sequence_no, bid.amount, bid.idempotency_key
			FROM bid
			JOIN auction_participant participant
				ON participant.auction_participant_id =
					bid.auction_participant_id
			WHERE participant.auction_room_id = ?
			ORDER BY bid.sequence_no
			""", (resultSet, rowNumber) -> new StoredBidRow(
			resultSet.getInt("sequence_no"),
			resultSet.getLong("amount"),
			resultSet.getString("idempotency_key")
		), auctionId);
	}

	private List<Long> walletBalances(Long walletId) {
		return jdbcTemplate.queryForObject("""
			SELECT available_balance, held_balance
			FROM wallet
			WHERE wallet_id = ?
			""", (resultSet, rowNumber) -> List.of(
			resultSet.getLong("available_balance"),
			resultSet.getLong("held_balance")
		), walletId);
	}

	private List<LedgerRow> ledgers(Long walletId) {
		return jdbcTemplate.query("""
			SELECT transaction_type, available_delta, held_delta
			FROM trade_point_ledger
			WHERE wallet_id = ?
			ORDER BY trade_point_ledger_id
			""", (resultSet, rowNumber) -> new LedgerRow(
			resultSet.getString("transaction_type"),
			resultSet.getLong("available_delta"),
			resultSet.getLong("held_delta")
		), walletId);
	}

	private record Fixture(
		Long auctionId,
		Long bidderId,
		Long tradeWalletId
	) {
	}

	private record StoredBidRow(
		int sequenceNo,
		long amount,
		String idempotencyKey
	) {
	}

	private record LedgerRow(
		String transactionType,
		long availableDelta,
		long heldDelta
	) {
	}
}
