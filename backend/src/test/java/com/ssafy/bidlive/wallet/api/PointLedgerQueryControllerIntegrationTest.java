package com.ssafy.bidlive.wallet.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** 실제 JWT와 PostgreSQL을 거쳐 통합 원장 조회 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PointLedgerQueryControllerIntegrationTest {

	private static final String PATH =
		"/api/v1/members/me/point-ledgers";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void combinesTradeAndBettingLedgersInNewestOrder() throws Exception {
		Fixture fixture = createFixture();
		long chargeLedgerId = insertTradeLedger(
			fixture.tradeWalletId(),
			"CHARGE",
			100_000L,
			0L,
			100_000L,
			0L,
			"ledger-charge",
			"2026-07-20T10:00:00+09:00"
		);
		long grantLedgerId = insertBettingLedger(
			fixture.bettingWalletId(),
			"GRANT",
			5_000L,
			0L,
			5_000L,
			0L,
			"ledger-grant",
			"2026-07-20T11:00:00+09:00"
		);
		long holdLedgerId = insertTradeLedger(
			fixture.tradeWalletId(),
			"HOLD",
			-82_000L,
			82_000L,
			18_000L,
			82_000L,
			"ledger-hold",
			"2026-07-20T12:00:00+09:00"
		);

		mockMvc.perform(get(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(fixture.member())
				))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].ledgerId")
				.value(holdLedgerId))
			.andExpect(jsonPath("$.items[0].walletType")
				.value("TRADE"))
			.andExpect(jsonPath("$.items[0].transactionType")
				.value("HOLD"))
			.andExpect(jsonPath("$.items[0].availableChange")
				.value(-82_000))
			.andExpect(jsonPath("$.items[0].heldChange")
				.value(82_000))
			.andExpect(jsonPath("$.items[0].description")
				.value("경매 입찰 포인트 보류"))
			.andExpect(jsonPath("$.items[0].referenceType")
				.value("BID"))
			.andExpect(jsonPath("$.items[1].ledgerId")
				.value(grantLedgerId))
			.andExpect(jsonPath("$.items[1].walletType")
				.value("BETTING"))
			.andExpect(jsonPath("$.items[1].referenceType")
				.value("REWARD"))
			.andExpect(jsonPath("$.items[2].ledgerId")
				.value(chargeLedgerId))
			.andExpect(jsonPath("$.items[2].referenceType")
				.value("PAYMENT"))
			.andExpect(jsonPath("$.nextCursor").doesNotExist())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void filtersAndContinuesWithOpaqueCursor() throws Exception {
		Fixture fixture = createFixture();
		long newest = insertTradeLedger(
			fixture.tradeWalletId(),
			"HOLD",
			-100L,
			100L,
			900L,
			100L,
			"ledger-page-1",
			"2026-07-20T12:00:00+09:00"
		);
		long next = insertTradeLedger(
			fixture.tradeWalletId(),
			"HOLD",
			-200L,
			200L,
			700L,
			300L,
			"ledger-page-2",
			"2026-07-20T11:00:00+09:00"
		);
		insertBettingLedger(
			fixture.bettingWalletId(),
			"BET",
			-300L,
			0L,
			4_700L,
			0L,
			"ledger-filtered-out",
			"2026-07-20T13:00:00+09:00"
		);

		MvcResult firstPage = mockMvc.perform(get(PATH)
				.param("walletType", "TRADE")
				.param("transactionType", "HOLD")
				.param("size", "1")
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(fixture.member())
				))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].ledgerId").value(newest))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.nextCursor").isString())
			.andReturn();
		JsonNode response = objectMapper.readTree(
			firstPage.getResponse().getContentAsByteArray()
		);
		String cursor = response.path("nextCursor").asText();

		mockMvc.perform(get(PATH)
				.param("walletType", "TRADE")
				.param("transactionType", "HOLD")
				.param("size", "1")
				.param("cursor", cursor)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(fixture.member())
				))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].ledgerId").value(next))
			.andExpect(jsonPath("$.nextCursor").doesNotExist())
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void rejectsInvalidRequestsAndMissingWallet() throws Exception {
		Fixture fixture = createFixture();

		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get(PATH)
				.param("walletType", "CASH")
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(fixture.member())
				))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code")
				.value("INVALID_LEDGER_FILTER"));

		mockMvc.perform(get(PATH)
				.param("cursor", "broken")
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(fixture.member())
				))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));

		Member memberWithoutWallet = createMember();
		mockMvc.perform(get(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(memberWithoutWallet)
				))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
	}

	private Fixture createFixture() {
		Member member = createMember();
		Long tradeWalletId = insertWallet(member.getId(), "TRADE");
		Long bettingWalletId = insertWallet(member.getId(), "BETTING");
		return new Fixture(member, tradeWalletId, bettingWalletId);
	}

	private Member createMember() {
		return memberRepository.saveAndFlush(Member.createOAuthMember());
	}

	private Long insertWallet(Long memberId, String walletType) {
		return jdbcTemplate.queryForObject(
			"""
				INSERT INTO wallet (
					member_id,
					wallet_type,
					available_balance,
					held_balance
				)
				VALUES (?, ?, 0, 0)
				RETURNING wallet_id
				""",
			Long.class,
			memberId,
			walletType
		);
	}

	private long insertTradeLedger(
		Long walletId,
		String transactionType,
		long availableDelta,
		long heldDelta,
		long availableBalanceAfter,
		long heldBalanceAfter,
		String idempotencyKey,
		String createdAt
	) {
		Long ledgerId = jdbcTemplate.queryForObject(
			"""
				INSERT INTO trade_point_ledger (
					wallet_id,
					transaction_type,
					available_delta,
					held_delta,
					available_balance_after,
					held_balance_after,
					idempotency_key,
					created_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				RETURNING trade_point_ledger_id
				""",
			Long.class,
			walletId,
			transactionType,
			availableDelta,
			heldDelta,
			availableBalanceAfter,
			heldBalanceAfter,
			idempotencyKey,
			OffsetDateTime.parse(createdAt)
		);
		return ledgerId == null ? 0L : ledgerId;
	}

	private long insertBettingLedger(
		Long walletId,
		String transactionType,
		long availableDelta,
		long heldDelta,
		long availableBalanceAfter,
		long heldBalanceAfter,
		String idempotencyKey,
		String createdAt
	) {
		Long ledgerId = jdbcTemplate.queryForObject(
			"""
				INSERT INTO betting_point_ledger (
					wallet_id,
					transaction_type,
					available_delta,
					held_delta,
					available_balance_after,
					held_balance_after,
					idempotency_key,
					created_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				RETURNING betting_point_ledger_id
				""",
			Long.class,
			walletId,
			transactionType,
			availableDelta,
			heldDelta,
			availableBalanceAfter,
			heldBalanceAfter,
			idempotencyKey,
			OffsetDateTime.parse(createdAt)
		);
		return ledgerId == null ? 0L : ledgerId;
	}

	private String bearerAccessToken(Member member) {
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}

	private record Fixture(
		Member member,
		Long tradeWalletId,
		Long bettingWalletId
	) {
	}
}
