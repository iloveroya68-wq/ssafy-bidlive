package com.ssafy.bidlive.member.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberTermAgreement;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 Security와 JPA를 통해 MEM-02 약관 조회 API 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TermsControllerIntegrationTest {

	private static final String PATH = "/api/v1/terms?purpose=ONBOARDING";
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
	void returnsLatestActiveTermsAndCurrentVersionAgreementsForOnboardingMember()
		throws Exception {
		Member member = memberRepository.saveAndFlush(Member.createOAuthMember());
		Term oldServiceTerm = persistTerm(
			TermCode.SERVICE_TERMS,
			"서비스 이용약관",
			"이전 서비스 약관",
			"1.0",
			true,
			1,
			NOW.minusSeconds(86_400),
			null
		);
		Term currentServiceTerm = persistTerm(
			TermCode.SERVICE_TERMS,
			"서비스 이용약관",
			"현재 서비스 약관",
			"2.0",
			true,
			1,
			NOW.minusSeconds(3_600),
			null
		);
		persistTerm(
			TermCode.SERVICE_TERMS,
			"서비스 이용약관",
			"미래 서비스 약관",
			"3.0",
			true,
			1,
			NOW.plusSeconds(86_400),
			null
		);
		Term privacyTerm = persistTerm(
			TermCode.PRIVACY_POLICY,
			"개인정보 수집 및 이용 동의",
			"개인정보 약관",
			"1.0",
			true,
			2,
			NOW.minusSeconds(3_600),
			null
		);
		persistTerm(
			TermCode.MARKETING_CONSENT,
			"마케팅 정보 수신 동의",
			"만료된 마케팅 약관",
			"0.9",
			false,
			3,
			NOW.minusSeconds(172_800),
			NOW.minusSeconds(86_400)
		);
		Term marketingTerm = persistTerm(
			TermCode.MARKETING_CONSENT,
			"마케팅 정보 수신 동의",
			"현재 마케팅 약관",
			"1.0",
			false,
			3,
			NOW.minusSeconds(3_600),
			null
		);
		persistAgreement(member.getId(), oldServiceTerm.getId(), true);
		persistAgreement(member.getId(), currentServiceTerm.getId(), true);
		persistAgreement(member.getId(), marketingTerm.getId(), false);
		entityManager.flush();
		entityManager.clear();

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].termId").value(currentServiceTerm.getId()))
			.andExpect(jsonPath("$.items[0].code").value("SERVICE_TERMS"))
			.andExpect(jsonPath("$.items[0].title").value("서비스 이용약관"))
			.andExpect(jsonPath("$.items[0].content").value("현재 서비스 약관"))
			.andExpect(jsonPath("$.items[0].version").value("2.0"))
			.andExpect(jsonPath("$.items[0].required").value(true))
			.andExpect(jsonPath("$.items[0].agreed").value(true))
			.andExpect(jsonPath("$.items[1].termId").value(privacyTerm.getId()))
			.andExpect(jsonPath("$.items[1].code").value("PRIVACY_POLICY"))
			.andExpect(jsonPath("$.items[1].agreed").value(false))
			.andExpect(jsonPath("$.items[2].termId").value(marketingTerm.getId()))
			.andExpect(jsonPath("$.items[2].code").value("MARKETING_CONSENT"))
			.andExpect(jsonPath("$.items[2].required").value(false))
			.andExpect(jsonPath("$.items[2].agreed").value(false));
	}

	@Test
	void rejectsUnauthenticatedRequest() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsUnsupportedPurpose() throws Exception {
		Member member = memberRepository.saveAndFlush(Member.createOAuthMember());

		mockMvc.perform(get("/api/v1/terms?purpose=SIGNUP")
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TERMS_PURPOSE"));
	}

	@Test
	void rejectsTokenForMissingMember() throws Exception {
		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(999L)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	@Test
	void rejectsWhenNoActiveTermsExist() throws Exception {
		Member member = memberRepository.saveAndFlush(Member.createOAuthMember());

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ACTIVE_TERMS_NOT_FOUND"));
	}

	private Term persistTerm(
		TermCode code,
		String title,
		String content,
		String version,
		boolean required,
		int displayOrder,
		Instant effectiveAt,
		Instant expiresAt
	) {
		Term term = Term.create(
			TermsPurpose.ONBOARDING,
			code,
			title,
			content,
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

	private void persistAgreement(Long memberId, Long termId, boolean agreed) {
		entityManager.persist(MemberTermAgreement.record(memberId, termId, agreed, NOW));
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
