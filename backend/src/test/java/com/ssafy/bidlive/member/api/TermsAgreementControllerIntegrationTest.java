package com.ssafy.bidlive.member.api;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.Term;
import com.ssafy.bidlive.member.domain.TermCode;
import com.ssafy.bidlive.member.domain.TermsPurpose;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 Security와 JPA를 통해 MEM-02 약관 동의 API 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TermsAgreementControllerIntegrationTest {

	private static final String PATH = "/api/v1/members/me/terms-agreements";
	private static final Instant NOW = Instant.now();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void storesCurrentAgreementsAndCompletesOnboardingWithNickname() throws Exception {
		Member member = member("희원");
		TermsFixture terms = currentTerms();

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content(requestBody(terms, true, true, false)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.onboardingCompleted").value(true))
			.andExpect(jsonPath("$.agreedAt", endsWith("+09:00")));

		assertThatAgreementCount(member.getId(), 3L);
		assertThatAgreement(member.getId(), terms.serviceTerm().getId(), true);
		assertThatAgreement(member.getId(), terms.privacyTerm().getId(), true);
		assertThatAgreement(member.getId(), terms.marketingTerm().getId(), false);
	}

	@Test
	void keepsOnboardingIncompleteBeforeNicknameConfiguration() throws Exception {
		Member member = member(null);
		TermsFixture terms = currentTerms();

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content(requestBody(terms, true, true, false)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.onboardingCompleted").value(false));

		assertThatAgreementCount(member.getId(), 3L);
	}

	@Test
	void handlesIdenticalRetryWithoutCreatingDuplicateRows() throws Exception {
		Member member = member("희원");
		TermsFixture terms = currentTerms();
		String requestBody = requestBody(terms, true, true, false);

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content(requestBody))
			.andExpect(status().isOk());
		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content(requestBody))
			.andExpect(status().isOk());

		assertThatAgreementCount(member.getId(), 3L);
	}

	@Test
	void rejectsMissingOrMalformedAgreementList() throws Exception {
		Member member = member("희원");

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"agreements\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TERMS_REQUEST"));
		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"agreements\":[{\"termId\":1}]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TERMS_REQUEST"));
		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"agreements\":[null]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TERMS_REQUEST"));
	}

	@Test
	void rejectsDuplicateTermId() throws Exception {
		Member member = member("희원");

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "agreements": [
				    {"termId": 1, "agreed": true},
				    {"termId": 1, "agreed": false}
				  ]
				}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DUPLICATE_TERM_ID"));
	}

	@Test
	void rejectsMissingMemberAndUnauthenticatedRequest() throws Exception {
		mockMvc.perform(post(PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"agreements\":[{\"termId\":1,\"agreed\":true}]}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(999L))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"agreements\":[{\"termId\":1,\"agreed\":true}]}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	@Test
	void rejectsUnknownTerm() throws Exception {
		Member member = member("희원");

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"agreements\":[{\"termId\":999,\"agreed\":true}]}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("TERM_NOT_FOUND"));
	}

	@Test
	void rejectsInactiveAndOutdatedTermsWithDifferentCodes() throws Exception {
		Member member = member("희원");
		Term futureTerm = persistTerm(
			TermCode.SERVICE_TERMS,
			"3.0",
			true,
			1,
			NOW.plusSeconds(86_400),
			null
		);

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content(singleAgreement(futureTerm.getId(), true)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TERM_NOT_ACTIVE"));

		Term oldTerm = persistTerm(
			TermCode.PRIVACY_POLICY,
			"1.0",
			true,
			2,
			NOW.minusSeconds(7_200),
			null
		);
		persistTerm(
			TermCode.PRIVACY_POLICY,
			"2.0",
			true,
			2,
			NOW.minusSeconds(3_600),
			null
		);

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content(singleAgreement(oldTerm.getId(), true)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TERMS_VERSION_OUTDATED"));
	}

	@Test
	void rejectsMissingOrRefusedRequiredTermAndRollsBackAllAgreements() throws Exception {
		Member member = member("희원");
		TermsFixture terms = currentTerms();

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content(requestBody(terms, true, false, false)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REQUIRED_TERMS_NOT_AGREED"));

		assertThatAgreementCount(member.getId(), 0L);
	}

	private Member member(String nickname) {
		Member member = Member.createOAuthMember();
		ReflectionTestUtils.setField(member, "nickname", nickname);
		return memberRepository.saveAndFlush(member);
	}

	private TermsFixture currentTerms() {
		return new TermsFixture(
			persistTerm(
				TermCode.SERVICE_TERMS,
				"1.0",
				true,
				1,
				NOW.minusSeconds(3_600),
				null
			),
			persistTerm(
				TermCode.PRIVACY_POLICY,
				"1.0",
				true,
				2,
				NOW.minusSeconds(3_600),
				null
			),
			persistTerm(
				TermCode.MARKETING_CONSENT,
				"1.0",
				false,
				3,
				NOW.minusSeconds(3_600),
				null
			)
		);
	}

	private Term persistTerm(
		TermCode code,
		String version,
		boolean required,
		int displayOrder,
		Instant effectiveAt,
		Instant expiresAt
	) {
		Term term = Term.create(
			TermsPurpose.ONBOARDING,
			code,
			code.name(),
			code.name() + " content",
			version,
			required,
			displayOrder,
			effectiveAt,
			expiresAt
		);
		entityManager.persist(term);
		entityManager.flush();
		return term;
	}

	private String requestBody(
		TermsFixture terms,
		boolean serviceAgreed,
		boolean privacyAgreed,
		boolean marketingAgreed
	) {
		return """
			{
			  "agreements": [
			    {"termId": %d, "agreed": %s},
			    {"termId": %d, "agreed": %s},
			    {"termId": %d, "agreed": %s}
			  ]
			}
			""".formatted(
			terms.serviceTerm().getId(),
			serviceAgreed,
			terms.privacyTerm().getId(),
			privacyAgreed,
			terms.marketingTerm().getId(),
			marketingAgreed
		);
	}

	private String singleAgreement(Long termId, boolean agreed) {
		return """
			{"agreements":[{"termId":%d,"agreed":%s}]}
			""".formatted(termId, agreed);
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

	private void assertThatAgreementCount(Long memberId, Long expectedCount) {
		Long count = entityManager.createQuery(
			"""
				select count(agreement)
				from MemberTermAgreement agreement
				where agreement.memberId = :memberId
				""",
			Long.class
		)
			.setParameter("memberId", memberId)
			.getSingleResult();
		org.assertj.core.api.Assertions.assertThat(count).isEqualTo(expectedCount);
	}

	private void assertThatAgreement(Long memberId, Long termId, boolean agreed) {
		Boolean storedAgreement = entityManager.createQuery(
			"""
				select agreement.agreed
				from MemberTermAgreement agreement
				where agreement.memberId = :memberId
				  and agreement.termId = :termId
				""",
			Boolean.class
		)
			.setParameter("memberId", memberId)
			.setParameter("termId", termId)
			.getSingleResult();
		org.assertj.core.api.Assertions.assertThat(storedAgreement).isEqualTo(agreed);
	}

	private record TermsFixture(
		Term serviceTerm,
		Term privacyTerm,
		Term marketingTerm
	) {
	}
}
