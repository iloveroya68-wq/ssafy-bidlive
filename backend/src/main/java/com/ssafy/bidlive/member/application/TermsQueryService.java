package com.ssafy.bidlive.member.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.bidlive.member.domain.Term;
import com.ssafy.bidlive.member.domain.TermCode;
import com.ssafy.bidlive.member.domain.TermsPurpose;
import com.ssafy.bidlive.member.domain.TermsQueryErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 현재 회원에게 적용되는 목적별 최신 약관과 동의 여부를 조회한다. */
@Service
public class TermsQueryService {

	private final MemberPersistence memberPersistence;
	private final TermsQueryPersistence termsQueryPersistence;
	private final Clock clock;

	@Autowired
	public TermsQueryService(
		MemberPersistence memberPersistence,
		TermsQueryPersistence termsQueryPersistence
	) {
		this(memberPersistence, termsQueryPersistence, Clock.systemUTC());
	}

	TermsQueryService(
		MemberPersistence memberPersistence,
		TermsQueryPersistence termsQueryPersistence,
		Clock clock
	) {
		this.memberPersistence = memberPersistence;
		this.termsQueryPersistence = termsQueryPersistence;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public TermsQueryResult getTerms(Long memberId, String purposeValue) {
		TermsPurpose purpose = parsePurpose(purposeValue);

		try {
			ensureMemberExists(memberId);
			List<Term> latestTerms = latestTerms(
				termsQueryPersistence.findActiveTerms(purpose, Instant.now(clock))
			);
			if (latestTerms.isEmpty()) {
				throw new TermsQueryException(TermsQueryErrorCode.ACTIVE_TERMS_NOT_FOUND);
			}

			Map<Long, Boolean> agreementStatuses = termsQueryPersistence.findAgreementStatuses(
				memberId,
				latestTerms.stream().map(Term::getId).toList()
			);
			return new TermsQueryResult(latestTerms.stream()
				.map(term -> toItem(term, agreementStatuses))
				.toList());
		} catch (TermsQueryException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new TermsQueryException(
				TermsQueryErrorCode.TERMS_SERVICE_UNAVAILABLE,
				exception
			);
		}
	}

	private TermsPurpose parsePurpose(String purposeValue) {
		if (purposeValue == null || purposeValue.isBlank()) {
			throw new TermsQueryException(TermsQueryErrorCode.INVALID_TERMS_PURPOSE);
		}
		try {
			return TermsPurpose.valueOf(purposeValue);
		} catch (IllegalArgumentException exception) {
			throw new TermsQueryException(
				TermsQueryErrorCode.INVALID_TERMS_PURPOSE,
				exception
			);
		}
	}

	private void ensureMemberExists(Long memberId) {
		if (memberId == null || memberPersistence.findById(memberId).isEmpty()) {
			throw new TermsQueryException(TermsQueryErrorCode.MEMBER_NOT_FOUND);
		}
	}

	private List<Term> latestTerms(List<Term> activeTerms) {
		Map<TermCode, Term> latestByCode = new LinkedHashMap<>();
		activeTerms.forEach(term -> latestByCode.putIfAbsent(term.getCode(), term));

		List<Term> latestTerms = new ArrayList<>(latestByCode.values());
		latestTerms.sort(
			Comparator.comparingInt(Term::getDisplayOrder)
				.thenComparing(Term::getId, Comparator.nullsLast(Long::compareTo))
		);
		return latestTerms;
	}

	private TermsQueryItem toItem(Term term, Map<Long, Boolean> agreementStatuses) {
		return new TermsQueryItem(
			term.getId(),
			term.getCode(),
			term.getTitle(),
			term.getContent(),
			term.getVersion(),
			term.isRequired(),
			agreementStatuses.getOrDefault(term.getId(), false)
		);
	}
}
