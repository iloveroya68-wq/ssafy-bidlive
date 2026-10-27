package com.ssafy.bidlive.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.Term;
import com.ssafy.bidlive.member.domain.TermCode;
import com.ssafy.bidlive.member.domain.TermsPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 닉네임과 최신 필수 약관 동의가 모두 충족될 때만 온보딩이 완료되는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class OnboardingStatusServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-28T06:00:00Z");

	@Mock
	private TermsQueryPersistence termsQueryPersistence;

	@Mock
	private Member member;

	private OnboardingStatusService service;

	@BeforeEach
	void setUp() {
		service = new OnboardingStatusService(
			termsQueryPersistence,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void requiresOnboardingWhenNicknameIsMissing() {
		when(member.getNickname()).thenReturn(null);

		assertThat(service.isOnboardingRequired(member)).isTrue();
		verify(termsQueryPersistence, never())
			.findActiveTerms(TermsPurpose.ONBOARDING, NOW);
	}

	@Test
	void requiresEveryLatestRequiredTermAgreement() {
		Term latestService = term(2L, TermCode.SERVICE_TERMS, "2.0", true);
		Term oldService = term(1L, TermCode.SERVICE_TERMS, "1.0", true);
		Term privacy = term(3L, TermCode.PRIVACY_POLICY, "1.0", true);
		Term marketing = term(4L, TermCode.MARKETING_CONSENT, "1.0", false);
		when(member.getNickname()).thenReturn("희원");
		when(member.getId()).thenReturn(25L);
		when(termsQueryPersistence.findActiveTerms(TermsPurpose.ONBOARDING, NOW))
			.thenReturn(List.of(
				latestService,
				oldService,
				privacy,
				marketing
			));
		when(termsQueryPersistence.findAgreementStatuses(25L, List.of(2L, 3L)))
			.thenReturn(Map.of(2L, true));

		assertThat(service.isOnboardingRequired(member)).isTrue();

		when(termsQueryPersistence.findAgreementStatuses(25L, List.of(2L, 3L)))
			.thenReturn(Map.of(2L, true, 3L, true));
		assertThat(service.isOnboardingRequired(member)).isFalse();
	}

	private Term term(
		Long id,
		TermCode code,
		String version,
		boolean required
	) {
		Term term = Term.create(
			TermsPurpose.ONBOARDING,
			code,
			code.name(),
			"content",
			version,
			required,
			id.intValue(),
			NOW.minusSeconds(id),
			null
		);
		ReflectionTestUtils.setField(term, "id", id);
		return term;
	}
}
