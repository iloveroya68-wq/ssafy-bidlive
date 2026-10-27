package com.ssafy.bidlive.member.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.ssafy.bidlive.member.domain.Term;
import com.ssafy.bidlive.member.domain.TermsPurpose;

/** 약관 조회 유스케이스가 요구하는 영속성 계약이다. */
public interface TermsQueryPersistence {

	List<Term> findActiveTerms(TermsPurpose purpose, Instant now);

	Map<Long, Boolean> findAgreementStatuses(Long memberId, Collection<Long> termIds);
}
