package com.ssafy.bidlive.auction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.ResultWrite;
import com.ssafy.bidlive.auction.application.AuctionSettlementPointResult;
import com.ssafy.bidlive.auction.application.AuctionSettlementPointService;
import com.ssafy.bidlive.auction.application.AuctionSettlementResult;
import com.ssafy.bidlive.auction.application.AuctionSettlementStatus;
import com.ssafy.bidlive.auction.application.AuctionSettlementTransactionService;
import com.ssafy.bidlive.auction.domain.VickreyPriceDecisionType;
import com.ssafy.bidlive.auction.domain.VickreySettlementDecision;
import com.ssafy.bidlive.auction.domain.VickreySettlementOutcome;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 BID-07 조회·포인트 원장·결과 저장을 검증한다. */
@SpringBootTest
@Transactional
class JpaAuctionSettlementAdapterIntegrationTest {

	private static final long AUCTION_ID = 9761L;
	private static final Instant END_AT =
		Instant.parse("2026-07-20T12:20:00Z");

	@Autowired
	private JpaAuctionSettlementAdapter adapter;

	@Autowired
	private AuctionSettlementPointService pointService;

	@Autowired
	private AuctionSettlementTransactionService transactionService;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void insertFixture() {
		execute("""
			INSERT INTO product_category (
				category_id, code, name, is_active, display_order
			)
			VALUES (9761, 'TEST_SETTLEMENT', '정산 테스트', TRUE, 9761)
			""");
		execute("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(9761, '정산판매자', 'USER', 'ACTIVE'),
				(9762, '정산입찰자1', 'USER', 'ACTIVE'),
				(9763, '정산입찰자2', 'USER', 'ACTIVE')
			""");
		execute("""
			INSERT INTO product (
				product_id, seller_member_id, category_id,
				title, description, moderation_status
			)
			VALUES (
				9761, 9761, 9761,
				'정산할 방 상품', '설명', 'PASSED'
			)
			""");
		execute("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price,
				duration_minutes, start_type, started_at,
				ends_at, status, visibility
			)
			VALUES (
				9761, 9761, 50000,
				15, 'IMMEDIATE', '2026-07-20T12:05:00Z',
				'2026-07-20T12:20:00Z', 'LIVE', 'PUBLIC'
			)
			""");
		execute("""
			INSERT INTO auction_participant (
				auction_participant_id, auction_room_id,
				member_id, participation_type, participated_at
			)
			VALUES
				(9762, 9761, 9762, 'BIDDER', '2026-07-20T12:10:00Z'),
				(9763, 9761, 9763, 'BIDDER', '2026-07-20T12:11:00Z')
			""");
		execute("""
			INSERT INTO bid (
				bid_id, auction_participant_id, amount,
				sequence_no, idempotency_key, submitted_at
			)
			VALUES
				(9761, 9762, 60000, 1, 'settlement-bid-1', '2026-07-20T12:12:00Z'),
				(9762, 9763, 74000, 1, 'settlement-bid-2', '2026-07-20T12:13:00Z'),
				(9763, 9762, 82000, 2, 'settlement-bid-3', '2026-07-20T12:14:00Z')
			""");
		execute("""
			INSERT INTO wallet (
				wallet_id, member_id, wallet_type,
				available_balance, held_balance
			)
			VALUES
				(9762, 9762, 'TRADE', 18000, 82000),
				(9763, 9763, 'TRADE', 26000, 74000)
			""");
		execute("""
			INSERT INTO trade_point_ledger (
				wallet_id, transaction_type,
				available_delta, held_delta,
				available_balance_after, held_balance_after,
				reference_type, reference_id, idempotency_key
			)
			VALUES
				(
					9762, 'HOLD', -60000, 60000, 40000, 60000,
					'BID', 9761, 'settlement-hold-1'
				),
				(
					9763, 'HOLD', -74000, 74000, 26000, 74000,
					'BID', 9762, 'settlement-hold-2'
				),
				(
					9762, 'HOLD', -22000, 22000, 18000, 82000,
					'BID', 9763, 'settlement-hold-3'
				)
			""");
		entityManager.flush();
	}

	@Test
	void locksAndReadsLiveSettlementTarget() {
		assertThat(adapter.lockTarget(AUCTION_ID)).hasValueSatisfying(target -> {
			assertThat(target.auctionId()).isEqualTo(AUCTION_ID);
			assertThat(target.status()).isEqualTo("LIVE");
			assertThat(target.reservePrice()).isEqualTo(50_000L);
			assertThat(target.endAt()).isEqualTo(END_AT);
			assertThat(target.resultExists()).isFalse();
		});
	}

	@Test
	void readsOnlyLatestBidPerMemberAndAllBidCounts() {
		assertThat(adapter.findFinalBids(AUCTION_ID))
			.extracting("bidId", "participantId", "memberId", "amount")
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple(
					9763L, 9762L, 9762L, 82_000L
				),
				org.assertj.core.groups.Tuple.tuple(
					9762L, 9763L, 9763L, 74_000L
				)
			);
		assertThat(adapter.countBids(AUCTION_ID).validBidderCount())
			.isEqualTo(2);
		assertThat(adapter.countBids(AUCTION_ID).totalBidCount())
			.isEqualTo(3);
	}

	@Test
	void readsOutstandingHoldsInWalletLockOrder() {
		assertThat(adapter.findOutstandingBidHolds(AUCTION_ID))
			.extracting("bidId", "memberId", "remainingHold")
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple(
					9761L, 9762L, 60_000L
				),
				org.assertj.core.groups.Tuple.tuple(
					9763L, 9762L, 22_000L
				),
				org.assertj.core.groups.Tuple.tuple(
					9762L, 9763L, 74_000L
				)
			);
	}

	@Test
	void writesSettlementAndReleaseLedgersToRealWallets() {
		VickreySettlementDecision decision =
			VickreySettlementDecision.sold(
				9762L,
				82_000L,
				74_000L,
				74_000L,
				VickreyPriceDecisionType.SECOND_PRICE
			);

		AuctionSettlementPointResult result = pointService.settle(
			AUCTION_ID,
			decision
		);
		entityManager.flush();
		entityManager.clear();

		assertThat(result.settledAmount()).isEqualTo(74_000L);
		assertThat(result.releasedAmount()).isEqualTo(82_000L);
		assertThat(walletBalances(9762L)).containsExactly(26_000L, 0L);
		assertThat(walletBalances(9763L)).containsExactly(100_000L, 0L);
		assertThat(settlementLedgerRows()).containsExactly(
			"bid-07:9761:bid:9761:settle:0:-60000",
			"bid-07:9761:bid:9762:release:74000:-74000",
			"bid-07:9761:bid:9763:release:8000:-8000",
			"bid-07:9761:bid:9763:settle:0:-14000"
		);
	}

	@Test
	void commitsFullSoldSettlementAndReusesStoredResult() {
		AuctionSettlementResult result = transactionService.settle(
			AUCTION_ID,
			END_AT
		);
		entityManager.flush();
		entityManager.clear();

		assertThat(result.status()).isEqualTo(AuctionSettlementStatus.SETTLED);
		assertThat(result.finalPrice()).isEqualTo(74_000L);
		assertThat(roomStatus()).isEqualTo("ENDED");
		assertThat(walletBalances(9762L)).containsExactly(26_000L, 0L);
		assertThat(walletBalances(9763L)).containsExactly(100_000L, 0L);
		assertThat(dealRow()).containsExactly(
			9761L,
			9762L,
			74_000L,
			74_000L,
			"HELD",
			"WAITING_ADDRESS"
		);

		AuctionSettlementResult retried = transactionService.settle(
			AUCTION_ID,
			END_AT.plusSeconds(1)
		);
		assertThat(retried.status())
			.isEqualTo(AuctionSettlementStatus.SETTLED);
		assertThat(retried.newlySettled()).isFalse();
		assertThat(dealCount()).isEqualTo(1L);
	}

	@Test
	void insertsSoldResultOnceAndMarksLiveRoomEnded() {
		ResultWrite result = new ResultWrite(
			AUCTION_ID,
			VickreySettlementOutcome.SOLD,
			9762L,
			9763L,
			82_000L,
			74_000L,
			74_000L,
			2,
			3
		);

		assertThat(adapter.createResultIfAbsent(result)).isPresent();
		assertThat(adapter.createResultIfAbsent(result)).isEmpty();
		assertThat(adapter.markEnded(AUCTION_ID, END_AT)).isEqualTo(1);
		assertThat(adapter.markEnded(AUCTION_ID, END_AT)).isZero();
		entityManager.flush();
		entityManager.clear();

		Object[] stored = (Object[]) entityManager.createNativeQuery("""
			SELECT
				result_status, termination_reason,
				winner_participant_id, winning_bid_id,
				highest_bid_amount, second_highest_bid_amount,
				final_price, valid_bidder_count, total_bid_count
			FROM auction_result
			WHERE auction_room_id = :auctionId
			""")
			.setParameter("auctionId", AUCTION_ID)
			.getSingleResult();
		assertThat(stored).containsExactly(
			"SOLD",
			"NORMAL",
			9762L,
			9763L,
			82_000L,
			74_000L,
			74_000L,
			2,
			3
		);
		assertThat(roomStatus()).isEqualTo("ENDED");
		assertThat(actualEndedAt()).isEqualTo(END_AT);
		assertThat(scheduledEndAt()).isEqualTo(END_AT);
	}

	@Test
	void insertsNoBidResultWithoutWinnerOrPrices() {
		execute("DELETE FROM bid WHERE bid_id IN (9761, 9762, 9763)");
		execute("""
			DELETE FROM auction_participant
			WHERE auction_participant_id IN (9762, 9763)
			""");

		ResultWrite result = new ResultWrite(
			AUCTION_ID,
			VickreySettlementOutcome.NO_SALE,
			null,
			null,
			null,
			null,
			null,
			0,
			0
		);

		assertThat(adapter.createResultIfAbsent(result)).isPresent();
		entityManager.flush();

		Object[] stored = (Object[]) entityManager.createNativeQuery("""
			SELECT
				result_status, termination_reason,
				winner_participant_id, winning_bid_id, final_price
			FROM auction_result
			WHERE auction_room_id = :auctionId
			""")
			.setParameter("auctionId", AUCTION_ID)
			.getSingleResult();
		assertThat(stored).containsExactly(
			"NO_SALE", "NO_BID", null, null, null
		);
	}

	private String roomStatus() {
		return (String) roomColumn("status");
	}

	private Instant scheduledEndAt() {
		return toInstant(roomColumn("ends_at"));
	}

	private Instant actualEndedAt() {
		return toInstant(roomColumn("ended_at"));
	}

	private Object[] walletBalances(long memberId) {
		return (Object[]) entityManager.createNativeQuery("""
			SELECT available_balance, held_balance
			FROM wallet
			WHERE member_id = :memberId
				AND wallet_type = 'TRADE'
			""")
			.setParameter("memberId", memberId)
			.getSingleResult();
	}

	private Object[] dealRow() {
		return (Object[]) entityManager.createNativeQuery("""
			SELECT
				seller_member_id, buyer_member_id,
				final_price, escrow_amount, escrow_status, deal_status
			FROM deal
			WHERE auction_result_id = (
				SELECT auction_result_id
				FROM auction_result
				WHERE auction_room_id = :auctionId
			)
			""")
			.setParameter("auctionId", AUCTION_ID)
			.getSingleResult();
	}

	private long dealCount() {
		return ((Number) entityManager.createNativeQuery("""
			SELECT COUNT(*)
			FROM deal
			WHERE auction_result_id = (
				SELECT auction_result_id
				FROM auction_result
				WHERE auction_room_id = :auctionId
			)
			""")
			.setParameter("auctionId", AUCTION_ID)
			.getSingleResult()).longValue();
	}

	private List<String> settlementLedgerRows() {
		List<?> rows = entityManager.createNativeQuery("""
			SELECT idempotency_key, available_delta, held_delta
			FROM trade_point_ledger
			WHERE idempotency_key LIKE 'bid-07:%'
			ORDER BY idempotency_key
			""")
			.getResultList();
		return rows.stream()
			.map(Object[].class::cast)
			.map(row -> row[0] + ":" +
				((Number) row[1]).longValue() + ":" +
				((Number) row[2]).longValue())
			.toList();
	}

	private Object roomColumn(String column) {
		return entityManager.createNativeQuery(
			"SELECT " + column
				+ " FROM auction_room WHERE auction_room_id = :auctionId"
		)
			.setParameter("auctionId", AUCTION_ID)
			.getSingleResult();
	}

	private static Instant toInstant(Object value) {
		return switch (value) {
			case java.sql.Timestamp timestamp -> timestamp.toInstant();
			case java.time.OffsetDateTime offsetDateTime ->
				offsetDateTime.toInstant();
			case Instant instant -> instant;
			default -> throw new IllegalStateException(
				"지원하지 않는 시각 타입: " + value.getClass()
			);
		};
	}

	private void execute(String sql) {
		entityManager.createNativeQuery(sql).executeUpdate();
	}
}
