package com.ssafy.bidlive.member.api;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.application.port.out.NicknameProhibitedWordSource;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberTermAgreement;
import com.ssafy.bidlive.member.domain.Term;
import com.ssafy.bidlive.member.domain.TermCode;
import com.ssafy.bidlive.member.domain.TermsPurpose;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 JWT, HTTP와 JPA를 통해 닉네임 수정 및 온보딩 응답 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileUpdateControllerIntegrationTest {

	private static final String PATH = "/api/v1/members/me";
	private static final Instant NOW = Instant.now();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private EntityManager entityManager;

	@MockitoBean
	private NicknameProhibitedWordSource prohibitedWordSource;

	@BeforeEach
	void setUp() {
		when(prohibitedWordSource.getActiveWords()).thenReturn(Set.of());
	}

	@Test
	void updatesTrimmedNicknameAndCompletesOnboardingAfterRequiredAgreements()
		throws Exception {
		Member member = member(null);
		Term serviceTerm = requiredTerm(TermCode.SERVICE_TERMS, 1);
		Term privacyTerm = requiredTerm(TermCode.PRIVACY_POLICY, 2);
		agree(member, serviceTerm);
		agree(member, privacyTerm);

		mockMvc.perform(patch(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":\"  새닉네임25  \"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberId").value(member.getId()))
			.andExpect(jsonPath("$.nickname").value("새닉네임25"))
			.andExpect(jsonPath("$.onboardingRequired").value(false))
			.andExpect(jsonPath("$.updatedAt", endsWith("+09:00")));

		Member updated = memberRepository.findById(member.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(updated.getNickname())
			.isEqualTo("새닉네임25");
		org.assertj.core.api.Assertions.assertThat(updated.getNormalizedNickname())
			.isEqualTo("새닉네임25");
	}

	@Test
	void keepsOnboardingRequiredUntilCurrentRequiredTermsAreAgreed()
		throws Exception {
		Member member = member(null);
		requiredTerm(TermCode.SERVICE_TERMS, 1);

		mockMvc.perform(patch(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":\"희원25\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.onboardingRequired").value(true));
	}

	@Test
	void rejectsInvalidAndProhibitedNickname() throws Exception {
		Member member = member(null);
		String accessToken = bearerAccessToken(member);

		mockMvc.perform(patch(PATH)
			.header(HttpHeaders.AUTHORIZATION, accessToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":\"닉 네임\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));

		when(prohibitedWordSource.getActiveWords()).thenReturn(Set.of("admin"));
		mockMvc.perform(patch(PATH)
			.header(HttpHeaders.AUTHORIZATION, accessToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":\"BestAdmin1\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
	}

	@Test
	void rejectsEmptyRequestBody() throws Exception {
		Member member = member(null);
		String accessToken = bearerAccessToken(member);

		mockMvc.perform(patch(PATH)
			.header(HttpHeaders.AUTHORIZATION, accessToken)
			.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EMPTY_UPDATE_REQUEST"));
		mockMvc.perform(patch(PATH)
			.header(HttpHeaders.AUTHORIZATION, accessToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EMPTY_UPDATE_REQUEST"));
	}

	@Test
	void rejectsAnotherMembersNicknameIgnoringCaseAndAllowsOwnNickname()
		throws Exception {
		Member owner = member("HeeWon");
		Member requester = member(null);

		mockMvc.perform(patch(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(requester))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":\"heewon\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_EXISTS"));

		mockMvc.perform(patch(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(owner))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":\"HEEWON\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("HEEWON"));
	}

	@Test
	void rejectsUnauthenticatedAndMissingMember() throws Exception {
		mockMvc.perform(patch(PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":\"희원25\"}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(patch(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(999L))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":\"희원25\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	private Member member(String nickname) {
		Member member = Member.createOAuthMember();
		ReflectionTestUtils.setField(member, "nickname", nickname);
		return memberRepository.saveAndFlush(member);
	}

	private Term requiredTerm(TermCode code, int displayOrder) {
		Term term = Term.create(
			TermsPurpose.ONBOARDING,
			code,
			code.name(),
			"content",
			"1.0",
			true,
			displayOrder,
			NOW.minusSeconds(60),
			null
		);
		entityManager.persist(term);
		entityManager.flush();
		return term;
	}

	private void agree(Member member, Term term) {
		entityManager.persist(
			MemberTermAgreement.record(member.getId(), term.getId(), true, NOW)
		);
		entityManager.flush();
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
