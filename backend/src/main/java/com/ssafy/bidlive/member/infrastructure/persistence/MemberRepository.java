package com.ssafy.bidlive.member.infrastructure.persistence;

import java.util.Optional;

import com.ssafy.bidlive.member.domain.Member;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select member from Member member where member.id = :memberId")
	Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

	boolean existsByNormalizedNicknameAndIdNot(
		String normalizedNickname,
		Long memberId
	);
}
