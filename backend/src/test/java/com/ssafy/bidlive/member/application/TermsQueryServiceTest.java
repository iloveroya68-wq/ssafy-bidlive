package com.ssafy.bidlive.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.Term;
import com.ssafy.bidlive.member.domain.TermCode;
import com.ssafy.bidlive.member.domain.TermsPurpose;
import com.ssafy.bidlive.member.domain.TermsQueryErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 약관 조회 서비스의 최신 버전 선택과 오류 분류 규칙을 검증한다. */
@ExtendWith(MockitoExtension.class)
class TermsQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

	@Mock
	private MemberPersistence memberPersistence;

	@Mock
	private TermsQueryPersistence termsQueryPersistence;

	private TermsQueryService service;

	@BeforeEach
	void setUp() {
		service = new TermsQueryService(
			memberPersistence,
			termsQueryPersistence,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void returnsLatestTermPerCodeInDisplayOrderWithAgreementStatus() {
		Member member = mock(Member.class);
		Term currentServiceTerm = term(
			2L,
			TermCode.SERVICE_TERMS,
			"2.0",
			true,
			1
		);
		Term oldServiceTerm = mock(Term.class);
		when(oldServiceTerm.getCode()).thenReturn(TermCode.SERVICE_TERMS);
		Term marketingTerm = term(
			3L,
			TermCode.MARKETING_CONSENT,
			"1.0",
			false,
			3
		);
		when(memberPersistence.findById(25L)).thenReturn(Optional.of(member));
		when(termsQueryPersistence.findActiveTerms(TermsPurpose.ONBOARDING, NOW))
			.thenReturn(List.of(currentServiceTerm, oldServiceTerm, marketingTerm));
		when(termsQueryPersistence.findAgreementStatuses(25L, List.of(2L, 3L)))
			.thenReturn(Map.of(2L, true));

		TermsQueryResult result = service.getTerms(25L, "ONBOARDING");

		assertThat(result.items())
			.extracting(
				TermsQueryItem::termId,
				TermsQueryItem::version,
				TermsQueryItem::agreed
			)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple(2L, "2.0", true),
				org.assertj.core.groups.Tuple.tuple(3L, "1.0", false)
			);
	}

	@Test
	void rejectsMissingOrUnsupportedPurposeBeforePersistenceAccess() {
		assertErrorCode(
			() -> service.getTerms(25L, null),
			TermsQueryErrorCode.INVALID_TERMS_PURPOSE
		);
		assertErrorCode(
			() -> service.getTerms(25L, "SIGNUP"),
			TermsQueryErrorCode.INVALID_TERMS_PURPOSE
		);
		verifyNoInteractions(memberPersistence, termsQueryPersistence);
	}

	@Test
	void rejectsAuthenticationForMissingMember() {
		when(memberPersistence.findById(999L)).thenReturn(Optional.empty());

		assertErrorCode(
			() -> service.getTerms(999L, "ONBOARDING"),
			TermsQueryErrorCode.MEMBER_NOT_FOUND
		);
		verifyNoInteractions(termsQueryPersistence);
	}

	@Test
	void rejectsWhenNoActiveTermsExist() {
		when(memberPersistence.findById(25L)).thenReturn(Optional.of(mock(Member.class)));
		when(termsQueryPersistence.findActiveTerms(TermsPurpose.ONBOARDING, NOW))
			.thenReturn(List.of());

		assertErrorCode(
			() -> service.getTerms(25L, "ONBOARDING"),
			TermsQueryErrorCode.ACTIVE_TERMS_NOT_FOUND
		);
	}

	@Test
	void convertsUnexpectedPersistenceFailureToServiceUnavailable() {
		when(memberPersistence.findById(25L))
			.thenThrow(new IllegalStateException("database unavailable"));

		assertThatThrownBy(() -> service.getTerms(25L, "ONBOARDING"))
			.isInstanceOfSatisfying(TermsQueryException.class, exception -> {
				assertThat(exception.getErrorCode())
					.isEqualTo(TermsQueryErrorCode.TERMS_SERVICE_UNAVAILABLE);
				assertThat(exception.getCause())
					.isInstanceOf(IllegalStateException.class);
			});
	}

	private Term term(
		Long termId,
		TermCode code,
		String version,
		boolean required,
		int displayOrder
	) {
		Term term = mock(Term.class);
		when(term.getId()).thenReturn(termId);
		when(term.getCode()).thenReturn(code);
		when(term.getTitle()).thenReturn(code.name());
		when(term.getContent()).thenReturn(code.name() + " content");
		when(term.getVersion()).thenReturn(version);
		when(term.isRequired()).thenReturn(required);
		when(term.getDisplayOrder()).thenReturn(displayOrder);
		return term;
	}

	private void assertErrorCode(Runnable invocation, TermsQueryErrorCode errorCode) {
		assertThatThrownBy(invocation::run)
			.isInstanceOfSatisfying(TermsQueryException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(errorCode)
			);
	}
}
