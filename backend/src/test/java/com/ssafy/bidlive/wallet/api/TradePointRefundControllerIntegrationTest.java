package com.ssafy.bidlive.wallet.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** 실제 JWT와 PostgreSQL에서 P0 환불 API의 지갑·원장 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TradePointRefundControllerIntegrationTest {

	private static final String PATH =
		"/api/v1/members/me/wallets/trade/refunds";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void refundsAvailablePointsAndCreatesRefundLedger() throws Exception {
		Member member = createMember();
		Long walletId = insertTradeWallet(
			member.getId(),
			150_000L,
			50_000L
		);
		String idempotencyKey = UUID.randomUUID().toString();

		performRefund(member, idempotencyKey, 30_000L)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.walletType").value("TRADE"))
			.andExpect(jsonPath("$.refundedAmount").value(30_000))
			.andExpect(jsonPath("$.balance").value(170_000))
			.andExpect(jsonPath("$.heldAmount").value(50_000))
			.andExpect(jsonPath("$.availableAmount").value(120_000))
			.andExpect(jsonPath("$.ledgerId").isNumber())
			.andExpect(jsonPath("$.refundedAt").isString());

		assertThat(walletBalances(walletId))
			.containsExactly(120_000L, 50_000L);
		assertThat(ledger(idempotencyKey)).containsExactly(
			"REFUND",
			-30_000L,
			0L,
			120_000L,
			50_000L,
			null,
			null,
			null,
			null
		);

		mockMvc.perform(get("/api/v1/members/me/point-ledgers")
				.param("walletType", "TRADE")
				.param("transactionType", "REFUND")
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].referenceType")
				.value("PAYMENT"))
			.andExpect(jsonPath("$.items[0].description")
				.value("거래 포인트 환불"));
	}

	@Test
	void rejectsDuplicateWithoutRefundingTwice() throws Exception {
		Member member = createMember();
		Long walletId = insertTradeWallet(member.getId(), 100_000L, 0L);
		String idempotencyKey = UUID.randomUUID().toString();

		performRefund(member, idempotencyKey, 30_000L)
			.andExpect(status().isOk());
		performRefund(member, idempotencyKey, 30_000L)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));

		assertThat(walletBalances(walletId))
			.containsExactly(70_000L, 0L);
		assertThat(ledgerCount(idempotencyKey)).isEqualTo(1);
	}

	@Test
	void rejectsInvalidAndInsufficientAmountsWithoutMutation()
		throws Exception {
		Member member = createMember();
		Long walletId = insertTradeWallet(member.getId(), 29_999L, 50_000L);

		performRefund(member, UUID.randomUUID().toString(), 0L)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code")
				.value("INVALID_REFUND_AMOUNT"));
		performRefund(member, UUID.randomUUID().toString(), 30_000L)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code")
				.value("INSUFFICIENT_AVAILABLE_AMOUNT"));

		assertThat(walletBalances(walletId))
			.containsExactly(29_999L, 50_000L);
	}

	@Test
	void rejectsMissingWalletAuthenticationAndMalformedRequest()
		throws Exception {
		Member member = createMember();

		performRefund(member, UUID.randomUUID().toString(), 30_000L)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code")
				.value("TRADE_WALLET_NOT_FOUND"));

		mockMvc.perform(post(PATH)
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":30000}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				)
				.header("Idempotency-Key", "not-uuid")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":30000}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code")
				.value("INVALID_REFUND_AMOUNT"));

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
				.value("INVALID_REFUND_AMOUNT"));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void preventsConcurrentRefundsFromExceedingAvailableAmount()
		throws Exception {
		Member member = createMember();
		Long walletId = insertTradeWallet(member.getId(), 100_000L, 50_000L);
		String firstKey = UUID.randomUUID().toString();
		String secondKey = UUID.randomUUID().toString();
		var start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);

		try {
			Future<Integer> first = executor.submit(() -> {
				start.await();
				return performRefund(member, firstKey, 60_000L)
					.andReturn()
					.getResponse()
					.getStatus();
			});
			Future<Integer> second = executor.submit(() -> {
				start.await();
				return performRefund(member, secondKey, 60_000L)
					.andReturn()
					.getResponse()
					.getStatus();
			});

			start.countDown();

			assertThat(java.util.List.of(first.get(), second.get()))
				.containsExactlyInAnyOrder(200, 422);
			assertThat(walletBalances(walletId))
				.containsExactly(40_000L, 50_000L);
			assertThat(
				ledgerCount(firstKey) + ledgerCount(secondKey)
			).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}

	private org.springframework.test.web.servlet.ResultActions performRefund(
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

	private Object[] walletBalances(Long walletId) {
		return jdbcTemplate.queryForObject(
			"""
				SELECT available_balance, held_balance
				FROM wallet
				WHERE wallet_id = ?
				""",
			(resultSet, rowNumber) -> new Object[] {
				resultSet.getLong("available_balance"),
				resultSet.getLong("held_balance")
			},
			walletId
		);
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
