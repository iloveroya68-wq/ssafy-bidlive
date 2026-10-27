package com.ssafy.bidlive.member.infrastructure.persistence;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.member.domain.Term;
import com.ssafy.bidlive.member.domain.TermsPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 버전별 약관을 저장하고 현재 유효한 약관 후보를 조회한다. */
interface TermRepository extends JpaRepository<Term, Long> {

	@Query("""
		select term
		from Term term
		where term.purpose = :purpose
		  and term.active = true
		  and term.effectiveAt <= :now
		  and (term.expiresAt is null or term.expiresAt > :now)
		order by term.effectiveAt desc, term.id desc
		""")
	List<Term> findActiveByPurpose(
		@Param("purpose") TermsPurpose purpose,
		@Param("now") Instant now
	);
}
