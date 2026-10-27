package com.ssafy.bidlive.member.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 JWT, PostgreSQL과 HTTP를 통해 프로필 조회 응답·상태별 오류를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileQueryControllerIntegrationTest {

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

	@Test
	void returnsOnboardedActiveMemberWithEmptyActivitySummary() throws Exception {
		Member member = member("준호25", MemberStatus.ACTIVE);
		completeRequiredAgreements(member);

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberId").value(member.getId()))
			.andExpect(jsonPath("$.nickname").value("준호25"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.activitySummary.salesCount").value(0))
			.andExpect(jsonPath("$.activitySummary.purchaseCount").value(0))
			.andExpect(jsonPath(
				"$.activitySummary.predictionParticipationCount"
			).value(0))
			.andExpect(jsonPath("$.activitySummary.predictionHitCount").value(0))
			.andExpect(jsonPath("$.activitySummary.predictionHitRate").value(0.0));
	}

	@Test
	void allowsSuspendedMemberProfileQuery() throws Exception {
		Member member = member("정지회원25", MemberStatus.SUSPENDED);
		completeRequiredAgreements(member);

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUSPENDED"));
	}

	@Test
	void rejectsUnauthenticatedAndMissingMember() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(999L)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
	}

	@Test
	void rejectsWithdrawnMember() throws Exception {
		Member member = member("탈퇴회원25", MemberStatus.WITHDRAWN);

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_AVAILABLE"));
	}

	@Test
	void rejectsMemberWithoutNicknameOrRequiredAgreements() throws Exception {
		Member member = member(null, MemberStatus.ACTIVE);

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(member)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ONBOARDING_NOT_COMPLETED"));
	}

	private Member member(String nickname, MemberStatus status) {
		Member member = Member.createOAuthMember();
		if (nickname != null) {
			member.changeNickname(nickname);
		}
		ReflectionTestUtils.setField(member, "status", status);
		if (status == MemberStatus.WITHDRAWN) {
			ReflectionTestUtils.setField(member, "withdrawnAt", NOW);
		}
		return memberRepository.saveAndFlush(member);
	}

	private void completeRequiredAgreements(Member member) {
		Term serviceTerm = requiredTerm(TermCode.SERVICE_TERMS, 1);
		Term privacyTerm = requiredTerm(TermCode.PRIVACY_POLICY, 2);
		agree(member, serviceTerm);
		agree(member, privacyTerm);
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
		Member member = org.mockito.Mockito.mock(Member.class);
		org.mockito.Mockito.when(member.getId()).thenReturn(memberId);
		org.mockito.Mockito.when(member.getRole()).thenReturn(MemberRole.USER);
		return bearerAccessToken(member);
	}
}
