package com.ssafy.bidlive.member.application;

import java.util.Optional;

import com.ssafy.bidlive.member.domain.Member;

/** 회원 Application Service가 사용하는 영속성 Port다. */
public interface MemberPersistence {

	Member save(Member member);

	Optional<Member> findById(Long memberId);

	Optional<Member> findByIdForUpdate(Long memberId);

	boolean existsByNormalizedNicknameAndIdNot(
		String normalizedNickname,
		Long memberId
	);

	Member saveProfile(Member member);
}
