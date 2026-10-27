package com.ssafy.bidlive.member.infrastructure.persistence;

import java.util.Collection;
import java.util.List;

import com.ssafy.bidlive.member.domain.MemberTermAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

/** 회원과 약관 버전별 동의 상태를 저장하고 조회한다. */
interface MemberTermAgreementRepository extends JpaRepository<MemberTermAgreement, Long> {

	List<MemberTermAgreement> findAllByMemberIdAndTermIdIn(
		Long memberId,
		Collection<Long> termIds
	);
}
