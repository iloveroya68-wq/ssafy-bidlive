package com.ssafy.bidlive.wallet.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 JWT와 PostgreSQL을 거쳐 거래·베팅 지갑 조회 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WalletQueryControllerIntegrationTest {

	private static final String PATH = "/api/v1/members/me/wallets";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void returnsBothWalletsAndCalculatedTotalBalances() throws Exception {
		Member member = createMember();
		insertWallet(member.getId(), "TRADE", 118_000L, 82_000L);
		insertWallet(member.getId(), "BETTING", 4_500L, 500L);

		mockMvc.perform(get(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tradeWallet.balance")
				.value(200_000))
			.andExpect(jsonPath("$.tradeWallet.heldAmount")
				.value(82_000))
			.andExpect(jsonPath("$.tradeWallet.availableAmount")
				.value(118_000))
			.andExpect(jsonPath("$.bettingWallet.balance")
				.value(5_000))
			.andExpect(jsonPath("$.bettingWallet.heldAmount")
				.value(500))
			.andExpect(jsonPath("$.bettingWallet.availableAmount")
				.value(4_500));
	}

	@Test
	void returnsZeroBalancesWithoutUsingOtherMembersWallets()
		throws Exception {
		Member currentMember = createMember();
		insertWallet(currentMember.getId(), "TRADE", 0L, 0L);
		insertWallet(currentMember.getId(), "BETTING", 0L, 0L);

		Member otherMember = createMember();
		insertWallet(otherMember.getId(), "TRADE", 999_999L, 1L);
		insertWallet(otherMember.getId(), "BETTING", 999_999L, 1L);

		mockMvc.perform(get(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(currentMember)
				))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tradeWallet.balance").value(0))
			.andExpect(jsonPath("$.tradeWallet.heldAmount").value(0))
			.andExpect(jsonPath("$.tradeWallet.availableAmount").value(0))
			.andExpect(jsonPath("$.bettingWallet.balance").value(0))
			.andExpect(jsonPath("$.bettingWallet.heldAmount").value(0))
			.andExpect(jsonPath("$.bettingWallet.availableAmount").value(0));
	}

	@Test
	void rejectsWhenRequiredWalletIsMissing() throws Exception {
		Member member = createMember();
		insertWallet(member.getId(), "TRADE", 100_000L, 0L);

		mockMvc.perform(get(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
	}

	@Test
	void rejectsMissingAuthenticationAndMissingMember() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(999_494L)
				))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	private Member createMember() {
		return memberRepository.saveAndFlush(Member.createOAuthMember());
	}

	private void insertWallet(
		Long memberId,
		String walletType,
		long availableBalance,
		long heldBalance
	) {
		jdbcTemplate.update(
			"""
				INSERT INTO wallet (
					member_id,
					wallet_type,
					available_balance,
					held_balance
				)
				VALUES (?, ?, ?, ?)
				""",
			memberId,
			walletType,
			availableBalance,
			heldBalance
		);
	}

	private String bearerAccessToken(Member member) {
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}

	private String bearerAccessToken(Long memberId) {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(memberId);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return bearerAccessToken(member);
	}
}
