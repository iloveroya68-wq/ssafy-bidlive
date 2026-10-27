package com.ssafy.bidlive.wallet.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 실제 JWT와 PostgreSQL에서 P0 충전 API의 지갑·원장 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TradePointChargeControllerIntegrationTest {

	private static final String PATH =
		"/api/v1/members/me/wallets/trade/charge";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void chargesTradeWalletAndCreatesChargeLedger() throws Exception {
		Member member = createMember();
		Long walletId = insertTradeWallet(member.getId(), 50_000L, 50_000L);
		String idempotencyKey = UUID.randomUUID().toString();

		mockMvc.perform(post(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				)
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":100000}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.walletType").value("TRADE"))
			.andExpect(jsonPath("$.chargedAmount").value(100_000))
			.andExpect(jsonPath("$.balance").value(200_000))
			.andExpect(jsonPath("$.heldAmount").value(50_000))
			.andExpect(jsonPath("$.availableAmount").value(150_000))
			.andExpect(jsonPath("$.ledgerId").isNumber())
			.andExpect(jsonPath("$.chargedAt").isString());

		assertThat(availableBalance(walletId)).isEqualTo(150_000L);
		assertThat(ledger(idempotencyKey)).containsExactly(
			"CHARGE",
			100_000L,
			0L,
			150_000L,
			50_000L,
			null,
			null,
			null,
			null
		);
	}

	@Test
	void rejectsDuplicateWithoutChargingTwice() throws Exception {
		Member member = createMember();
		Long walletId = insertTradeWallet(member.getId(), 0L, 0L);
		String idempotencyKey = UUID.randomUUID().toString();

		performCharge(member, idempotencyKey, 100_000L)
			.andExpect(status().isOk());
		performCharge(member, idempotencyKey, 100_000L)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));

		assertThat(availableBalance(walletId)).isEqualTo(100_000L);
		assertThat(ledgerCount(idempotencyKey)).isEqualTo(1);
	}

	@Test
	void rejectsInvalidAndLimitExceededAmounts() throws Exception {
		Member member = createMember();
		insertTradeWallet(member.getId(), 0L, 0L);

		performCharge(member, UUID.randomUUID().toString(), 0L)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code")
				.value("INVALID_CHARGE_AMOUNT"));
		performCharge(
			member,
			UUID.randomUUID().toString(),
			1_000_001L
		)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code")
				.value("CHARGE_LIMIT_EXCEEDED"));
	}

	@Test
	void rejectsDailyLimitExceeded() throws Exception {
		Member member = createMember();
		Long walletId = insertTradeWallet(
			member.getId(),
			2_500_000L,
			0L
		);
		insertChargeLedger(
			walletId,
			2_500_000L,
			UUID.randomUUID().toString()
		);

		performCharge(
			member,
			UUID.randomUUID().toString(),
			500_001L
		)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code")
				.value("CHARGE_LIMIT_EXCEEDED"));

		assertThat(availableBalance(walletId)).isEqualTo(2_500_000L);
	}

	@Test
	void rejectsMissingWalletAuthenticationAndMalformedBody()
		throws Exception {
		Member member = createMember();

		performCharge(member, UUID.randomUUID().toString(), 100_000L)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code")
				.value("TRADE_WALLET_NOT_FOUND"));

		mockMvc.perform(post(PATH)
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":100000}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				)
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code")
				.value("INVALID_CHARGE_AMOUNT"));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void chargesOnlyOnceForConcurrentRequestsWithSameKey()
		throws Exception {
		Member member = createMember();
		Long walletId = insertTradeWallet(member.getId(), 0L, 0L);
		String idempotencyKey = UUID.randomUUID().toString();
		var start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);

		try {
			Future<Integer> first = executor.submit(() -> {
				start.await();
				return performCharge(
					member,
					idempotencyKey,
					100_000L
				).andReturn().getResponse().getStatus();
			});
			Future<Integer> second = executor.submit(() -> {
				start.await();
				return performCharge(
					member,
					idempotencyKey,
					100_000L
				).andReturn().getResponse().getStatus();
			});

			start.countDown();

			assertThat(java.util.List.of(first.get(), second.get()))
				.containsExactlyInAnyOrder(200, 409);
			assertThat(availableBalance(walletId)).isEqualTo(100_000L);
			assertThat(ledgerCount(idempotencyKey)).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}

	private org.springframework.test.web.servlet.ResultActions performCharge(
		Member member,
		String idempotencyKey,
		long amount
	) throws Exception {
		return mockMvc.perform(post(PATH)
			.header(
				HttpHeaders.AUTHORIZATION,
				bearerAccessToken(member)
			)
			.header("Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"amount\":%d}".formatted(amount)));
	}

	private Member createMember() {
		return memberRepository.saveAndFlush(Member.createOAuthMember());
	}

	private Long insertTradeWallet(
		Long memberId,
		long availableBalance,
		long heldBalance
	) {
		return jdbcTemplate.queryForObject(
			"""
				INSERT INTO wallet (
					member_id,
					wallet_type,
					available_balance,
					held_balance
				)
				VALUES (?, 'TRADE', ?, ?)
				RETURNING wallet_id
				""",
			Long.class,
			memberId,
			availableBalance,
			heldBalance
		);
	}

	private void insertChargeLedger(
		Long walletId,
		long amount,
		String idempotencyKey
	) {
		jdbcTemplate.update(
			"""
				INSERT INTO trade_point_ledger (
					wallet_id,
					transaction_type,
					available_delta,
					held_delta,
					available_balance_after,
					held_balance_after,
					idempotency_key
				)
				VALUES (?, 'CHARGE', ?, 0, ?, 0, ?)
				""",
			walletId,
			amount,
			amount,
			idempotencyKey
		);
	}

	private long availableBalance(Long walletId) {
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

	private int ledgerCount(String idempotencyKey) {
		Integer count = jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM trade_point_ledger
				WHERE idempotency_key = ?
				""",
			Integer.class,
			idempotencyKey
		);
		return count == null ? 0 : count;
	}

	private Object[] ledger(String idempotencyKey) {
		return jdbcTemplate.queryForObject(
			"""
				SELECT
					transaction_type,
					available_delta,
					held_delta,
					available_balance_after,
					held_balance_after,
					auction_participant_id,
					bid_id,
					deal_id,
					point_charge_id
				FROM trade_point_ledger
				WHERE idempotency_key = ?
				""",
			(resultSet, rowNumber) -> new Object[] {
				resultSet.getString("transaction_type"),
				resultSet.getLong("available_delta"),
				resultSet.getLong("held_delta"),
				resultSet.getLong("available_balance_after"),
				resultSet.getLong("held_balance_after"),
				resultSet.getObject("auction_participant_id", Long.class),
				resultSet.getObject("bid_id", Long.class),
				resultSet.getObject("deal_id", Long.class),
				resultSet.getObject("point_charge_id", Long.class)
			},
			idempotencyKey
		);
	}

	private String bearerAccessToken(Member member) {
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}
}
