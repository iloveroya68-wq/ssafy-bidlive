package com.ssafy.bidlive.member.infrastructure.persistence;

import java.util.Collection;
import java.util.List;

import com.ssafy.bidlive.member.application.TermsAgreementPersistence;
import com.ssafy.bidlive.member.domain.MemberTermAgreement;
import com.ssafy.bidlive.member.domain.Term;
import org.springframework.stereotype.Repository;

/** 약관 동의 영속성 계약을 Spring Data JPA로 구현한다. */
@Repository
public class TermsAgreementPersistenceAdapter implements TermsAgreementPersistence {

	private final TermRepository termRepository;
	private final MemberTermAgreementRepository agreementRepository;

	public TermsAgreementPersistenceAdapter(
		TermRepository termRepository,
		MemberTermAgreementRepository agreementRepository
	) {
		this.termRepository = termRepository;
		this.agreementRepository = agreementRepository;
	}

	@Override
	public List<Term> findTermsByIds(Collection<Long> termIds) {
		return termRepository.findAllById(termIds);
	}

	@Override
	public List<MemberTermAgreement> findAgreements(
		Long memberId,
		Collection<Long> termIds
	) {
		return agreementRepository.findAllByMemberIdAndTermIdIn(memberId, termIds);
	}

	@Override
	public List<MemberTermAgreement> saveAll(List<MemberTermAgreement> agreements) {
		return agreementRepository.saveAllAndFlush(agreements);
	}
}
