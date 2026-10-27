package com.ssafy.bidlive.member.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.Term;
import com.ssafy.bidlive.member.domain.TermCode;
import com.ssafy.bidlive.member.domain.TermsPurpose;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 닉네임과 최신 필수 약관 동의를 기준으로 회원 온보딩 상태를 계산한다. */
@Component
public class OnboardingStatusService {

	private final TermsQueryPersistence termsQueryPersistence;
	private final Clock clock;

	@Autowired
	public OnboardingStatusService(TermsQueryPersistence termsQueryPersistence) {
		this(termsQueryPersistence, Clock.systemUTC());
	}

	OnboardingStatusService(
		TermsQueryPersistence termsQueryPersistence,
		Clock clock
	) {
		this.termsQueryPersistence = termsQueryPersistence;
		this.clock = clock;
	}

	public boolean isOnboardingRequired(Member member) {
		if (member.getNickname() == null || member.getNickname().isBlank()) {
			return true;
		}

		Instant now = clock.instant();
		List<Long> requiredTermIds = latestTerms(
			termsQueryPersistence.findActiveTerms(TermsPurpose.ONBOARDING, now)
		).stream()
			.filter(Term::isRequired)
			.map(Term::getId)
			.toList();
		Map<Long, Boolean> agreementStatuses =
			termsQueryPersistence.findAgreementStatuses(
				member.getId(),
				requiredTermIds
			);

		return requiredTermIds.stream()
			.anyMatch(termId -> !agreementStatuses.getOrDefault(termId, false));
	}

	private List<Term> latestTerms(List<Term> activeTerms) {
		Map<TermCode, Term> latestByCode = new LinkedHashMap<>();
		activeTerms.forEach(term -> latestByCode.putIfAbsent(term.getCode(), term));
		return List.copyOf(latestByCode.values());
	}
}
