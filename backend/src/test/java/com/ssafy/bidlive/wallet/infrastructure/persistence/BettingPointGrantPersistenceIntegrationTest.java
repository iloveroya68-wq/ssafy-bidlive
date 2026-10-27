package com.ssafy.bidlive.wallet.infrastructure.persistence;

import static com.ssafy.bidlive.wallet.domain.BettingPointGrantType.LIVE_WATCH;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.ssafy.bidlive.wallet.application.BettingPointGrantCommand;
import com.ssafy.bidlive.wallet.application.BettingPointGrantResult;
import com.ssafy.bidlive.wallet.application.BettingPointGrantService;
import com.ssafy.bidlive.wallet.domain.BettingPointGrantPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 중복 없는 일반 보상과 GRANT 원장을 검증한다. */
@SpringBootTest
@Transactional
class BettingPointGrantPersistenceIntegrationTest {

	private static final String SOURCE_ID = "AUCTION:100";

	@Autowired
	private BettingPointGrantService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void grantsOnceAndReturnsExistingResultForDuplicate() {
		WalletFixture fixture = createFixture();
		BettingPointGrantCommand command = new BettingPointGrantCommand(
			fixture.memberId(),
			LIVE_WATCH,
			SOURCE_ID,
			100L,
			BettingPointGrantPolicy.VERSION,
			Instant.parse("2026-07-22T01:30:00Z")
		);

		BettingPointGrantResult first = service.grant(command);
		BettingPointGrantResult duplicate = service.grant(command);

		assertThat(first.duplicated()).isFalse();
		assertThat(first.amount()).isEqualTo(100L);
		assertThat(first.balanceAfter()).isEqualTo(2_100L);
		assertThat(duplicate.duplicated()).isTrue();
		assertThat(duplicate.grantId()).isEqualTo(first.grantId());
		assertThat(duplicate.ledgerId()).isEqualTo(first.ledgerId());
		assertThat(duplicate.balanceAfter()).isEqualTo(2_100L);
		assertThat(walletAvailableBalance(fixture.walletId()))
			.isEqualTo(2_100L);
		assertThat(grantCount(fixture.memberId())).isEqualTo(1);
		assertThat(ledgerCount(fixture.walletId())).isEqualTo(1);
		assertThat(ledger(fixture.walletId())).containsExactly(
			"GRANT",
			100L,
			0L,
			2_100L,
			0L
		);
	}

	private WalletFixture createFixture() {
		Long memberId = jdbcTemplate.queryForObject(
			"""
				INSERT INTO member (nickname, normalized_nickname)
				VALUES ('betting-grant-member', 'betting-grant-member')
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
				VALUES (?, 'BETTING', 2000, 0)
				RETURNING wallet_id
				""",
			Long.class,
			memberId
		);
		return new WalletFixture(memberId, walletId);
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

	private int grantCount(Long memberId) {
		Integer count = jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM betting_point_grant
				WHERE member_id = ?
					AND grant_type = 'LIVE_WATCH'
					AND source_id = ?
				""",
			Integer.class,
			memberId,
			SOURCE_ID
		);
		return count == null ? 0 : count;
	}

	private int ledgerCount(Long walletId) {
		Integer count = jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM betting_point_ledger
				WHERE wallet_id = ?
					AND transaction_type = 'GRANT'
				""",
			Integer.class,
			walletId
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
					held_balance_after
				FROM betting_point_ledger
				WHERE wallet_id = ?
					AND transaction_type = 'GRANT'
				""",
			(resultSet, rowNumber) -> new Object[] {
				resultSet.getString("transaction_type"),
				resultSet.getLong("available_delta"),
				resultSet.getLong("held_delta"),
				resultSet.getLong("available_balance_after"),
				resultSet.getLong("held_balance_after")
			},
			walletId
		);
	}

	private record WalletFixture(Long memberId, Long walletId) {
	}
}
