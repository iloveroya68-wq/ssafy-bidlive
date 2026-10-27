package com.ssafy.bidlive.member.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ssafy.bidlive.member.application.TermsQueryPersistence;
import com.ssafy.bidlive.member.domain.MemberTermAgreement;
import com.ssafy.bidlive.member.domain.Term;
import com.ssafy.bidlive.member.domain.TermsPurpose;
import org.springframework.stereotype.Repository;

/** 약관 조회 영속성 계약을 Spring Data JPA로 구현한다. */
@Repository
public class TermsQueryPersistenceAdapter implements TermsQueryPersistence {

	private final TermRepository termRepository;
	private final MemberTermAgreementRepository agreementRepository;

	public TermsQueryPersistenceAdapter(
		TermRepository termRepository,
		MemberTermAgreementRepository agreementRepository
	) {
		this.termRepository = termRepository;
		this.agreementRepository = agreementRepository;
	}

	@Override
	public List<Term> findActiveTerms(TermsPurpose purpose, Instant now) {
		return termRepository.findActiveByPurpose(purpose, now);
	}

	@Override
	public Map<Long, Boolean> findAgreementStatuses(
		Long memberId,
		Collection<Long> termIds
	) {
		return agreementRepository.findAllByMemberIdAndTermIdIn(memberId, termIds)
			.stream()
			.collect(Collectors.toMap(
				MemberTermAgreement::getTermId,
				MemberTermAgreement::isAgreed
			));
	}
}
