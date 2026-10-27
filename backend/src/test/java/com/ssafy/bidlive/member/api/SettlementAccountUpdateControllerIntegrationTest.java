package com.ssafy.bidlive.member.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.application.EncryptedAccountNumber;
import com.ssafy.bidlive.member.application.SettlementAccountEncryptor;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 JWT·PostgreSQL·AES-GCM을 거쳐 정산 계좌 등록·변경 계약을 검증한다. */
@SpringBootTest(properties = {
	"SETTLEMENT_ACCOUNT_ENCRYPTION_KEY_BASE64="
		+ "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
	"SETTLEMENT_ACCOUNT_ENCRYPTION_KEY_VERSION=v1"
})
@AutoConfigureMockMvc
@Transactional
class SettlementAccountUpdateControllerIntegrationTest {

	private static final String PATH =
		"/api/v1/members/me/settlement-account";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private SettlementAccountEncryptor accountEncryptor;

	@Autowired
	private EntityManager entityManager;

	@Test
	void registersEncryptedAccountAndReturnsOnlyMaskedSummary()
		throws Exception {
		Member member = memberRepository.saveAndFlush(
			Member.createOAuthMember()
		);

		mockMvc.perform(put(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(
					"004",
					"123-456-789-012",
					"신희원"
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bankCode").value("004"))
			.andExpect(jsonPath("$.bankName").value("KB국민은행"))
			.andExpect(jsonPath("$.maskedAccountNumber")
				.value("123-****-**012"))
			.andExpect(jsonPath("$.accountHolderName").value("신희원"))
			.andExpect(jsonPath("$.verified").value(false))
			.andExpect(jsonPath("$.updatedAt").isString())
			.andExpect(jsonPath("$.accountNumber").doesNotExist())
			.andExpect(content().string(not(containsString(
				"123456789012"
			))));

		Object[] row = accountRow(member.getId());
		assertThat(row[0]).isNotEqualTo("123456789012");
		assertThat(row[1]).isNotNull();
		assertThat(row[2]).isEqualTo("v1");
		assertThat(row[3]).isEqualTo("123-****-**012");
		assertThat(accountEncryptor.decrypt(
			member.getId(),
			"004",
			new EncryptedAccountNumber(
				(String) row[0],
				(String) row[1],
				(String) row[2]
			)
		)).isEqualTo("123456789012");

		mockMvc.perform(get(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.account.maskedAccountNumber")
				.value("123-****-**012"))
			.andExpect(jsonPath("$.account.accountNumber").doesNotExist());
	}

	@Test
	void keepsOneRowAndOriginalCiphertextForSameRequest()
		throws Exception {
		Member member = memberRepository.saveAndFlush(
			Member.createOAuthMember()
		);

		performValidPut(member, "004", "123456789012", "신희원");
		Object[] first = accountRow(member.getId());

		performValidPut(member, "004", "123-456-789-012", " 신희원 ");
		Object[] second = accountRow(member.getId());

		assertThat(second[0]).isEqualTo(first[0]);
		assertThat(second[1]).isEqualTo(first[1]);
		assertThat(second[7]).isEqualTo(first[7]);
		Number count = (Number) entityManager.createNativeQuery("""
			SELECT COUNT(*)
			FROM settlement_account
			WHERE member_id = :memberId
			""")
			.setParameter("memberId", member.getId())
			.getSingleResult();
		assertThat(count.longValue()).isEqualTo(1L);
	}

	@Test
	void changesExistingAccountAndResetsVerification()
		throws Exception {
		Member member = memberRepository.saveAndFlush(
			Member.createOAuthMember()
		);
		performValidPut(member, "004", "123456789012", "신희원");
		entityManager.createNativeQuery("""
			UPDATE settlement_account
			SET verified = TRUE
			WHERE member_id = :memberId
			""")
			.setParameter("memberId", member.getId())
			.executeUpdate();

		mockMvc.perform(put(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(
					"088",
					"987654321098",
					"변경회원"
				)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bankCode").value("088"))
			.andExpect(jsonPath("$.bankName").value("신한은행"))
			.andExpect(jsonPath("$.maskedAccountNumber")
				.value("987-****-**098"))
			.andExpect(jsonPath("$.verified").value(false));

		Object[] row = accountRow(member.getId());
		assertThat(row[4]).isEqualTo("변경회원");
		assertThat(row[5]).isEqualTo(false);
		assertThat(accountEncryptor.decrypt(
			member.getId(),
			"088",
			new EncryptedAccountNumber(
				(String) row[0],
				(String) row[1],
				(String) row[2]
			)
		)).isEqualTo("987654321098");
	}

	@Test
	void rejectsInvalidRequestsAndMissingMember() throws Exception {
		Member member = memberRepository.saveAndFlush(
			Member.createOAuthMember()
		);

		mockMvc.perform(put(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(
					"999",
					"123456789012",
					"신희원"
				)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_BANK_CODE"));

		mockMvc.perform(put(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody("004", "1234", " ")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code")
				.value("INVALID_ACCOUNT_NUMBER"));

		mockMvc.perform(put(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(999_491L)
				)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(
					"004",
					"123456789012",
					"신희원"
				)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	@Test
	void rejectsMissingAuthenticationAndMalformedBody() throws Exception {
		Member member = memberRepository.saveAndFlush(
			Member.createOAuthMember()
		);

		mockMvc.perform(put(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(
					"004",
					"123456789012",
					"신희원"
				)))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(put(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code")
				.value("INVALID_ACCOUNT_NUMBER"));
	}

	private void performValidPut(
		Member member,
		String bankCode,
		String accountNumber,
		String accountHolderName
	) throws Exception {
		mockMvc.perform(put(PATH)
				.header(
					HttpHeaders.AUTHORIZATION,
					bearerAccessToken(member)
				)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody(
					bankCode,
					accountNumber,
					accountHolderName
				)))
			.andExpect(status().isOk());
	}

	private Object[] accountRow(Long memberId) {
		entityManager.flush();
		entityManager.clear();
		return (Object[]) entityManager.createNativeQuery("""
			SELECT
				encrypted_account_number,
				encryption_iv,
				encryption_key_version,
				masked_account_number,
				account_holder_name,
				verified,
				bank_code,
				updated_at
			FROM settlement_account
			WHERE member_id = :memberId
			""")
			.setParameter("memberId", memberId)
			.getSingleResult();
	}

	private String requestBody(
		String bankCode,
		String accountNumber,
		String accountHolderName
	) {
		return """
			{
			  "bankCode": "%s",
			  "accountNumber": "%s",
			  "accountHolderName": "%s"
			}
			""".formatted(bankCode, accountNumber, accountHolderName);
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
