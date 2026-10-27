package com.ssafy.bidlive.member.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ssafy.bidlive.member.application.TermsAgreementCommand.TermDecision;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberTermAgreement;
import com.ssafy.bidlive.member.domain.Term;
import com.ssafy.bidlive.member.domain.TermCode;
import com.ssafy.bidlive.member.domain.TermsAgreementErrorCode;
import com.ssafy.bidlive.member.domain.TermsPurpose;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최신 온보딩 약관 동의를 저장하고 회원의 온보딩 완료 여부를 계산한다. */
@Service
public class TermsAgreementService {

	private final MemberPersistence memberPersistence;
	private final TermsQueryPersistence termsQueryPersistence;
	private final TermsAgreementPersistence termsAgreementPersistence;
	private final Clock clock;

	@Autowired
	public TermsAgreementService(
		MemberPersistence memberPersistence,
		TermsQueryPersistence termsQueryPersistence,
		TermsAgreementPersistence termsAgreementPersistence
	) {
		this(
			memberPersistence,
			termsQueryPersistence,
			termsAgreementPersistence,
			Clock.systemUTC()
		);
	}

	TermsAgreementService(
		MemberPersistence memberPersistence,
		TermsQueryPersistence termsQueryPersistence,
		TermsAgreementPersistence termsAgreementPersistence,
		Clock clock
	) {
		this.memberPersistence = memberPersistence;
		this.termsQueryPersistence = termsQueryPersistence;
		this.termsAgreementPersistence = termsAgreementPersistence;
		this.clock = clock;
	}

	@Transactional
	public TermsAgreementResult agree(Long memberId, TermsAgreementCommand command) {
		validateRequest(command);
		Instant now = Instant.now(clock);

		try {
			Member member = memberPersistence.findByIdForUpdate(memberId)
				.orElseThrow(() -> error(TermsAgreementErrorCode.MEMBER_NOT_FOUND));
			Map<Long, TermDecision> decisions = decisionsByTermId(command.agreements());
			List<Long> termIds = List.copyOf(decisions.keySet());
			Map<Long, Term> requestedTerms = requestedTerms(termIds);
			List<Term> latestTerms = latestTerms(
				termsQueryPersistence.findActiveTerms(TermsPurpose.ONBOARDING, now)
			);

			validateRequestedTerms(requestedTerms, latestTerms, now);
			validateRequiredTerms(decisions, latestTerms);

			List<MemberTermAgreement> savedAgreements = saveAgreements(
				memberId,
				decisions,
				termIds,
				now
			);
			boolean onboardingCompleted =
				hasNickname(member) && allRequiredTermsAgreed(decisions, latestTerms);
			Instant agreedAt = savedAgreements.stream()
				.map(MemberTermAgreement::getAgreedAt)
				.max(Comparator.naturalOrder())
				.orElse(now);
			return new TermsAgreementResult(onboardingCompleted, agreedAt);
		} catch (TermsAgreementException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new TermsAgreementException(
				TermsAgreementErrorCode.TERMS_AGREEMENT_UNAVAILABLE,
				exception
			);
		}
	}

	private void validateRequest(TermsAgreementCommand command) {
		if (command == null
			|| command.agreements() == null
			|| command.agreements().isEmpty()
			|| command.agreements().stream().anyMatch(this::invalidDecision)) {
			throw error(TermsAgreementErrorCode.INVALID_TERMS_REQUEST);
		}

		Set<Long> termIds = new HashSet<>();
		for (TermDecision decision : command.agreements()) {
			if (!termIds.add(decision.termId())) {
				throw error(TermsAgreementErrorCode.DUPLICATE_TERM_ID);
			}
		}
	}

	private boolean invalidDecision(TermDecision decision) {
		return decision == null
			|| decision.termId() == null
			|| decision.termId() <= 0
			|| decision.agreed() == null;
	}

	private Map<Long, TermDecision> decisionsByTermId(List<TermDecision> agreements) {
		Map<Long, TermDecision> decisions = new LinkedHashMap<>();
		agreements.forEach(decision -> decisions.put(decision.termId(), decision));
		return decisions;
	}

	private Map<Long, Term> requestedTerms(List<Long> termIds) {
		List<Term> terms = termsAgreementPersistence.findTermsByIds(termIds);
		if (terms.size() != termIds.size()) {
			throw error(TermsAgreementErrorCode.TERM_NOT_FOUND);
		}

		Map<Long, Term> requestedTerms = new HashMap<>();
		terms.forEach(term -> requestedTerms.put(term.getId(), term));
		return requestedTerms;
	}

	private void validateRequestedTerms(
		Map<Long, Term> requestedTerms,
		List<Term> latestTerms,
		Instant now
	) {
		Map<TermCode, Term> latestByCode = new HashMap<>();
		latestTerms.forEach(term -> latestByCode.put(term.getCode(), term));

		for (Term term : requestedTerms.values()) {
			if (term.getPurpose() != TermsPurpose.ONBOARDING || !term.isActiveAt(now)) {
				throw error(TermsAgreementErrorCode.TERM_NOT_ACTIVE);
			}
			Term latestTerm = latestByCode.get(term.getCode());
			if (latestTerm == null) {
				throw error(TermsAgreementErrorCode.TERM_NOT_ACTIVE);
			}
			if (!latestTerm.getId().equals(term.getId())) {
				throw error(TermsAgreementErrorCode.TERMS_VERSION_OUTDATED);
			}
		}
	}

	private void validateRequiredTerms(
		Map<Long, TermDecision> decisions,
		List<Term> latestTerms
	) {
		if (!allRequiredTermsAgreed(decisions, latestTerms)) {
			throw error(TermsAgreementErrorCode.REQUIRED_TERMS_NOT_AGREED);
		}
	}

	private boolean allRequiredTermsAgreed(
		Map<Long, TermDecision> decisions,
		List<Term> latestTerms
	) {
		return latestTerms.stream()
			.filter(Term::isRequired)
			.allMatch(term -> {
				TermDecision decision = decisions.get(term.getId());
				return decision != null && Boolean.TRUE.equals(decision.agreed());
			});
	}

	private List<MemberTermAgreement> saveAgreements(
		Long memberId,
		Map<Long, TermDecision> decisions,
		List<Long> termIds,
		Instant now
	) {
		Map<Long, MemberTermAgreement> existingByTermId = new HashMap<>();
		termsAgreementPersistence.findAgreements(memberId, termIds)
			.forEach(agreement -> existingByTermId.put(agreement.getTermId(), agreement));

		List<MemberTermAgreement> agreements = new ArrayList<>();
		for (TermDecision decision : decisions.values()) {
			MemberTermAgreement agreement = existingByTermId.get(decision.termId());
			if (agreement == null) {
				agreement = MemberTermAgreement.record(
					memberId,
					decision.termId(),
					decision.agreed(),
					now
				);
			} else {
				agreement.changeDecision(decision.agreed(), now);
			}
			agreements.add(agreement);
		}
		return termsAgreementPersistence.saveAll(agreements);
	}

	private List<Term> latestTerms(List<Term> activeTerms) {
		Map<TermCode, Term> latestByCode = new LinkedHashMap<>();
		activeTerms.forEach(term -> latestByCode.putIfAbsent(term.getCode(), term));
		return List.copyOf(latestByCode.values());
	}

	private boolean hasNickname(Member member) {
		return member.getNickname() != null && !member.getNickname().isBlank();
	}

	private TermsAgreementException error(TermsAgreementErrorCode errorCode) {
		return new TermsAgreementException(errorCode);
	}
}
