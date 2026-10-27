package com.ssafy.bidlive.member.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.bidlive.member.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

/** 대소문자만 다른 닉네임을 DB 정규화 유니크 제약이 최종 차단하는지 검증한다. */
@DataJpaTest
class MemberNormalizedNicknamePersistenceTest {

	@Autowired
	private MemberRepository memberRepository;

	@Test
	void rejectsCaseInsensitiveDuplicateAtDatabaseBoundary() {
		Member first = Member.createOAuthMember();
		first.changeNickname("HeeWon");
		memberRepository.saveAndFlush(first);

		Member second = Member.createOAuthMember();
		second.changeNickname("heewon");

		assertThatThrownBy(() -> memberRepository.saveAndFlush(second))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
