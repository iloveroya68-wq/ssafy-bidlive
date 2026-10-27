package com.ssafy.bidlive.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.member.application.TermsAgreementCommand.TermDecision;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberTermAgreement;
import com.ssafy.bidlive.member.domain.Term;
import com.ssafy.bidlive.member.domain.TermCode;
import com.ssafy.bidlive.member.domain.TermsAgreementErrorCode;
import com.ssafy.bidlive.member.domain.TermsPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 약관 동의 서비스의 검증, 멱등 저장 및 온보딩 계산 규칙을 검증한다. */
@ExtendWith(MockitoExtension.class)
class TermsAgreementServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-28T05:30:00Z");

	@Mock
	private MemberPersistence memberPersistence;

	@Mock
	private TermsQueryPersistence termsQueryPersistence;

	@Mock
	private TermsAgreementPersistence termsAgreementPersistence;

	@Captor
	private ArgumentCaptor<List<MemberTermAgreement>> agreementsCaptor;

	private TermsAgreementService service;

	@BeforeEach
	void setUp() {
		service = new TermsAgreementService(
			memberPersistence,
			termsQueryPersistence,
			termsAgreementPersistence,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void savesCurrentTermsAndCompletesOnboardingOnlyWithNickname() {
		Member member = member("희원");
		Term serviceTerm = term(1L, TermCode.SERVICE_TERMS, "1.0", true, NOW.minusSeconds(60));
		Term privacyTerm = term(2L, TermCode.PRIVACY_POLICY, "1.0", true, NOW.minusSeconds(60));
		Term marketingTerm = term(3L, TermCode.MARKETING_CONSENT, "1.0", false, NOW.minusSeconds(60));
		TermsAgreementCommand command = command(
			new TermDecision(1L, true),
			new TermDecision(2L, true),
			new TermDecision(3L, false)
		);
		when(memberPersistence.findByIdForUpdate(25L)).thenReturn(Optional.of(member));
		when(termsAgreementPersistence.findTermsByIds(List.of(1L, 2L, 3L)))
			.thenReturn(List.of(serviceTerm, privacyTerm, marketingTerm));
		when(termsQueryPersistence.findActiveTerms(TermsPurpose.ONBOARDING, NOW))
			.thenReturn(List.of(serviceTerm, privacyTerm, marketingTerm));
		when(termsAgreementPersistence.findAgreements(25L, List.of(1L, 2L, 3L)))
			.thenReturn(List.of());
		when(termsAgreementPersistence.saveAll(any())).thenAnswer(invocation ->
			invocation.getArgument(0)
		);

		TermsAgreementResult result = service.agree(25L, command);

		assertThat(result.onboardingCompleted()).isTrue();
		assertThat(result.agreedAt()).isEqualTo(NOW);
		verify(termsAgreementPersistence).saveAll(agreementsCaptor.capture());
		assertThat(agreementsCaptor.getValue())
			.extracting(
				MemberTermAgreement::getTermId,
				MemberTermAgreement::isAgreed
			)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple(1L, true),
				org.assertj.core.groups.Tuple.tuple(2L, true),
				org.assertj.core.groups.Tuple.tuple(3L, false)
			);
	}

	@Test
	void keepsOnboardingIncompleteUntilNicknameIsConfigured() {
		Member member = member(null);
		Term requiredTerm = term(1L, TermCode.SERVICE_TERMS, "1.0", true, NOW.minusSeconds(60));
		when(memberPersistence.findByIdForUpdate(25L)).thenReturn(Optional.of(member));
		when(termsAgreementPersistence.findTermsByIds(List.of(1L)))
			.thenReturn(List.of(requiredTerm));
		when(termsQueryPersistence.findActiveTerms(TermsPurpose.ONBOARDING, NOW))
			.thenReturn(List.of(requiredTerm));
		when(termsAgreementPersistence.findAgreements(25L, List.of(1L)))
			.thenReturn(List.of());
		when(termsAgreementPersistence.saveAll(any())).thenAnswer(invocation ->
			invocation.getArgument(0)
		);

		TermsAgreementResult result = service.agree(
			25L,
			command(new TermDecision(1L, true))
		);

		assertThat(result.onboardingCompleted()).isFalse();
	}

	@Test
	void preservesOneAgreementAndOriginalTimestampForIdenticalRetry() {
		Member member = member("희원");
		Term requiredTerm = term(1L, TermCode.SERVICE_TERMS, "1.0", true, NOW.minusSeconds(60));
		Instant firstAgreedAt = NOW.minusSeconds(300);
		MemberTermAgreement existing =
			MemberTermAgreement.record(25L, 1L, true, firstAgreedAt);
		when(memberPersistence.findByIdForUpdate(25L)).thenReturn(Optional.of(member));
		when(termsAgreementPersistence.findTermsByIds(List.of(1L)))
			.thenReturn(List.of(requiredTerm));
		when(termsQueryPersistence.findActiveTerms(TermsPurpose.ONBOARDING, NOW))
			.thenReturn(List.of(requiredTerm));
		when(termsAgreementPersistence.findAgreements(25L, List.of(1L)))
			.thenReturn(List.of(existing));
		when(termsAgreementPersistence.saveAll(List.of(existing)))
			.thenReturn(List.of(existing));

		TermsAgreementResult result = service.agree(
			25L,
			command(new TermDecision(1L, true))
		);

		assertThat(result.agreedAt()).isEqualTo(firstAgreedAt);
		verify(termsAgreementPersistence).saveAll(List.of(existing));
	}

	@Test
	void rejectsDuplicateTermIdBeforePersistenceAccess() {
		TermsAgreementCommand command = command(
			new TermDecision(1L, true),
			new TermDecision(1L, false)
		);

		assertErrorCode(
			() -> service.agree(25L, command),
			TermsAgreementErrorCode.DUPLICATE_TERM_ID
		);
		verifyNoInteractions(
			memberPersistence,
			termsQueryPersistence,
			termsAgreementPersistence
		);
	}

	@Test
	void rejectsMissingTerm() {
		when(memberPersistence.findByIdForUpdate(25L))
			.thenReturn(Optional.of(member("희원")));
		when(termsAgreementPersistence.findTermsByIds(List.of(99L)))
			.thenReturn(List.of());

		assertErrorCode(
			() -> service.agree(25L, command(new TermDecision(99L, true))),
			TermsAgreementErrorCode.TERM_NOT_FOUND
		);
	}

	@Test
	void distinguishesInactiveAndOutdatedTermVersions() {
		Member member = member("희원");
		Term futureTerm = term(
			3L,
			TermCode.SERVICE_TERMS,
			"3.0",
			true,
			NOW.plusSeconds(60)
		);
		when(memberPersistence.findByIdForUpdate(25L)).thenReturn(Optional.of(member));
		when(termsAgreementPersistence.findTermsByIds(List.of(3L)))
			.thenReturn(List.of(futureTerm));
		when(termsQueryPersistence.findActiveTerms(TermsPurpose.ONBOARDING, NOW))
			.thenReturn(List.of());

		assertErrorCode(
			() -> service.agree(25L, command(new TermDecision(3L, true))),
			TermsAgreementErrorCode.TERM_NOT_ACTIVE
		);

		Term expiredTerm = Term.create(
			TermsPurpose.ONBOARDING,
			TermCode.MARKETING_CONSENT,
			TermCode.MARKETING_CONSENT.name(),
			"expired content",
			"0.9",
			false,
			3,
			NOW.minusSeconds(120),
			NOW.minusSeconds(60)
		);
		ReflectionTestUtils.setField(expiredTerm, "id", 4L);
		when(termsAgreementPersistence.findTermsByIds(List.of(4L)))
			.thenReturn(List.of(expiredTerm));
		assertErrorCode(
			() -> service.agree(25L, command(new TermDecision(4L, false))),
			TermsAgreementErrorCode.TERM_NOT_ACTIVE
		);

		Term oldTerm = term(
			1L,
			TermCode.SERVICE_TERMS,
			"1.0",
			true,
			NOW.minusSeconds(120)
		);
		Term currentTerm = term(
			2L,
			TermCode.SERVICE_TERMS,
			"2.0",
			true,
			NOW.minusSeconds(60)
		);
		when(termsAgreementPersistence.findTermsByIds(List.of(1L)))
			.thenReturn(List.of(oldTerm));
		when(termsQueryPersistence.findActiveTerms(TermsPurpose.ONBOARDING, NOW))
			.thenReturn(List.of(currentTerm, oldTerm));

		assertErrorCode(
			() -> service.agree(25L, command(new TermDecision(1L, true))),
			TermsAgreementErrorCode.TERMS_VERSION_OUTDATED
		);
	}

	@Test
	void rejectsMissingOrRefusedRequiredTermsWithoutSaving() {
		Member member = member("희원");
		Term serviceTerm = term(1L, TermCode.SERVICE_TERMS, "1.0", true, NOW.minusSeconds(60));
		Term privacyTerm = term(2L, TermCode.PRIVACY_POLICY, "1.0", true, NOW.minusSeconds(60));
		when(memberPersistence.findByIdForUpdate(25L)).thenReturn(Optional.of(member));
		when(termsAgreementPersistence.findTermsByIds(List.of(1L)))
			.thenReturn(List.of(serviceTerm));
		when(termsQueryPersistence.findActiveTerms(TermsPurpose.ONBOARDING, NOW))
			.thenReturn(List.of(serviceTerm, privacyTerm));

		assertErrorCode(
			() -> service.agree(25L, command(new TermDecision(1L, true))),
			TermsAgreementErrorCode.REQUIRED_TERMS_NOT_AGREED
		);

		when(termsAgreementPersistence.findTermsByIds(List.of(1L, 2L)))
			.thenReturn(List.of(serviceTerm, privacyTerm));
		assertErrorCode(
			() -> service.agree(
				25L,
				command(
					new TermDecision(1L, true),
					new TermDecision(2L, false)
				)
			),
			TermsAgreementErrorCode.REQUIRED_TERMS_NOT_AGREED
		);
		verify(termsAgreementPersistence, never()).saveAll(any());
	}

	@Test
	void convertsUnexpectedPersistenceFailureToServiceUnavailable() {
		when(memberPersistence.findByIdForUpdate(25L))
			.thenThrow(new IllegalStateException("database unavailable"));

		assertThatThrownBy(() ->
			service.agree(25L, command(new TermDecision(1L, true)))
		).isInstanceOfSatisfying(TermsAgreementException.class, exception -> {
			assertThat(exception.getErrorCode())
				.isEqualTo(TermsAgreementErrorCode.TERMS_AGREEMENT_UNAVAILABLE);
			assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
		});
	}

	private Member member(String nickname) {
		Member member = Member.createOAuthMember();
		ReflectionTestUtils.setField(member, "id", 25L);
		ReflectionTestUtils.setField(member, "nickname", nickname);
		return member;
	}

	private Term term(
		Long termId,
		TermCode code,
		String version,
		boolean required,
		Instant effectiveAt
	) {
		Term term = Term.create(
			TermsPurpose.ONBOARDING,
			code,
			code.name(),
			code.name() + " content",
			version,
			required,
			code.ordinal() + 1,
			effectiveAt,
			null
		);
		ReflectionTestUtils.setField(term, "id", termId);
		return term;
	}

	private TermsAgreementCommand command(TermDecision... decisions) {
		return new TermsAgreementCommand(List.of(decisions));
	}

	private void assertErrorCode(Runnable invocation, TermsAgreementErrorCode errorCode) {
		assertThatThrownBy(invocation::run)
			.isInstanceOfSatisfying(TermsAgreementException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(errorCode)
			);
	}
}
