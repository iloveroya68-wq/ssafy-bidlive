package com.ssafy.bidlive.wallet.infrastructure.persistence;

import static com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode.DUPLICATE_LEDGER_REFERENCE;
import static com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType.BID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.ssafy.bidlive.wallet.application.TradePointOperationException;
import com.ssafy.bidlive.wallet.application.TradePointOperationResult;
import com.ssafy.bidlive.wallet.application.TradePointOperationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 홀드·해제·정산의 지갑·원장 원자성을 검증한다. */
@SpringBootTest
@Transactional
class TradePointOperationPersistenceIntegrationTest {

	private static final Long REFERENCE_ID = 351L;

	@Autowired
	private TradePointOperationService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void persistsHoldReleaseAndSettlementAgainstOneReference() {
		WalletFixture fixture = createFixture();

		TradePointOperationResult hold = service.hold(
			BID,
			REFERENCE_ID,
			fixture.memberId(),
			82_000L,
			"BID:351:HOLD"
		);
		TradePointOperationResult release = service.release(
			BID,
			REFERENCE_ID,
			fixture.memberId(),
			20_000L,
			"BID:351:RELEASE"
		);
		TradePointOperationResult settlement = service.settle(
			BID,
			REFERENCE_ID,
			fixture.memberId(),
			62_000L,
			"BID:351:SETTLEMENT"
		);

		assertThat(hold.availableAmount()).isEqualTo(118_000L);
		assertThat(hold.heldAmount()).isEqualTo(82_000L);
		assertThat(release.availableAmount()).isEqualTo(138_000L);
		assertThat(release.heldAmount()).isEqualTo(62_000L);
		assertThat(settlement.availableAmount()).isEqualTo(138_000L);
		assertThat(settlement.heldAmount()).isZero();
		assertThat(settlement.balance()).isEqualTo(138_000L);
		assertThat(walletBalances(fixture.walletId()))
			.containsExactly(138_000L, 0L);
		assertThat(ledgerRows(fixture.walletId())).containsExactly(
			new Object[] {
				"HOLD",
				-82_000L,
				82_000L,
				118_000L,
				82_000L,
				"BID",
				REFERENCE_ID
			},
			new Object[] {
				"RELEASE",
				20_000L,
				-20_000L,
				138_000L,
				62_000L,
				"BID",
				REFERENCE_ID
			},
			new Object[] {
				"SETTLEMENT",
				0L,
				-62_000L,
				138_000L,
				0L,
				"BID",
				REFERENCE_ID
			}
		);
	}

	@Test
	void rejectsASecondOperationForTheSameReference() {
		WalletFixture fixture = createFixture();
		service.hold(
			BID,
			REFERENCE_ID,
			fixture.memberId(),
			10_000L,
			"BID:351:HOLD"
		);

		assertThatThrownBy(() -> service.hold(
			BID,
			REFERENCE_ID,
			fixture.memberId(),
			10_000L,
			"BID:351:HOLD:SECOND"
		)).isInstanceOfSatisfying(
			TradePointOperationException.class,
			exception -> assertThat(exception.getErrorCode())
				.isEqualTo(DUPLICATE_LEDGER_REFERENCE)
		);

		assertThat(ledgerRows(fixture.walletId())).hasSize(1);
		assertThat(walletBalances(fixture.walletId()))
			.containsExactly(190_000L, 10_000L);
	}

	private WalletFixture createFixture() {
		Long memberId = jdbcTemplate.queryForObject(
			"""
				INSERT INTO member (nickname, normalized_nickname)
				VALUES (
					'trade-point-operation-member',
					'trade-point-operation-member'
				)
				RETURNING member_id
				""",
			Long.class
		);
		Long walletId = jdbcTemplate.queryForObject(
			"""
				INSERT INTO wallet (
					member_id,
					wallet_type,
					available_balance,
					held_balance
				)
				VALUES (?, 'TRADE', 200000, 0)
				RETURNING wallet_id
				""",
			Long.class,
			memberId
		);
		return new WalletFixture(memberId, walletId);
	}

	private long[] walletBalances(Long walletId) {
		return jdbcTemplate.queryForObject(
			"""
				SELECT available_balance, held_balance
				FROM wallet
				WHERE wallet_id = ?
				""",
			(resultSet, rowNumber) -> new long[] {
				resultSet.getLong("available_balance"),
				resultSet.getLong("held_balance")
			},
			walletId
		);
	}

	private List<Object[]> ledgerRows(Long walletId) {
		return jdbcTemplate.query(
			"""
				SELECT
					transaction_type,
					available_delta,
					held_delta,
					available_balance_after,
					held_balance_after,
					reference_type,
					reference_id
				FROM trade_point_ledger
				WHERE wallet_id = ?
				ORDER BY trade_point_ledger_id
				""",
			(resultSet, rowNumber) -> new Object[] {
				resultSet.getString("transaction_type"),
				resultSet.getLong("available_delta"),
				resultSet.getLong("held_delta"),
				resultSet.getLong("available_balance_after"),
				resultSet.getLong("held_balance_after"),
				resultSet.getString("reference_type"),
				resultSet.getLong("reference_id")
			},
			walletId
		);
	}

	private record WalletFixture(Long memberId, Long walletId) {
	}
}
