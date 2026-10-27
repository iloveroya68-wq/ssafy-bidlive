package com.ssafy.bidlive.member.api;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 JWT와 PostgreSQL을 거쳐 본인 정산 계좌 조회 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettlementAccountQueryControllerIntegrationTest {

	private static final String PATH =
		"/api/v1/members/me/settlement-account";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void returnsOnlyMaskedAccountSummaryWithServerBankName() throws Exception {
		Member member = memberRepository.saveAndFlush(
			Member.createOAuthMember()
		);
		insertAccount(
			member.getId(),
			"004",
			"123-****-**789",
			"신희원",
			false
		);

		mockMvc.perform(get(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.account.bankCode").value("004"))
			.andExpect(jsonPath("$.account.bankName").value("KB국민은행"))
			.andExpect(jsonPath("$.account.maskedAccountNumber")
				.value("123-****-**789"))
			.andExpect(jsonPath("$.account.accountHolderName").value("신희원"))
			.andExpect(jsonPath("$.account.verified").value(false))
			.andExpect(jsonPath("$.account.updatedAt").isString())
			.andExpect(jsonPath("$.account.accountNumber").doesNotExist())
			.andExpect(content().string(not(containsString("account_number"))));
	}

	@Test
	void returnsExplicitNullWhenCurrentMemberHasNoAccount() throws Exception {
		Member currentMember = memberRepository.saveAndFlush(
			Member.createOAuthMember()
		);
		Member otherMember = memberRepository.saveAndFlush(
			Member.createOAuthMember()
		);
		insertAccount(
			otherMember.getId(),
			"088",
			"987-****-**321",
			"다른회원",
			false
		);

		mockMvc.perform(get(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(currentMember)
				))
			.andExpect(status().isOk())
			.andExpect(content().json("""
				{"account": null}
				"""));
	}

	@Test
	void rejectsMissingAuthenticationAndMissingMember() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(999_490L)
				))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	private void insertAccount(
		Long memberId,
		String bankCode,
		String maskedAccountNumber,
		String accountHolderName,
		boolean verified
	) {
		entityManager.createNativeQuery("""
			INSERT INTO settlement_account (
				member_id,
				bank_code,
				masked_account_number,
				account_holder_name,
				verified
			)
			VALUES (
				:memberId,
				:bankCode,
				:maskedAccountNumber,
				:accountHolderName,
				:verified
			)
			""")
			.setParameter("memberId", memberId)
			.setParameter("bankCode", bankCode)
			.setParameter("maskedAccountNumber", maskedAccountNumber)
			.setParameter("accountHolderName", accountHolderName)
			.setParameter("verified", verified)
			.executeUpdate();
		entityManager.flush();
		entityManager.clear();
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
