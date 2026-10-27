package com.ssafy.bidlive.member.application;

import java.util.Collection;
import java.util.List;

import com.ssafy.bidlive.member.domain.MemberTermAgreement;
import com.ssafy.bidlive.member.domain.Term;

/** 약관 동의 유스케이스가 요구하는 영속성 계약이다. */
public interface TermsAgreementPersistence {

	List<Term> findTermsByIds(Collection<Long> termIds);

	List<MemberTermAgreement> findAgreements(Long memberId, Collection<Long> termIds);

	List<MemberTermAgreement> saveAll(List<MemberTermAgreement> agreements);
}
