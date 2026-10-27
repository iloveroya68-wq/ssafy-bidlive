package com.ssafy.bidlive.member.infrastructure.persistence;

import java.util.Optional;

import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.application.ProfileUpdateException;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.ProfileUpdateErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** MemberPersistence 계약을 Spring Data JPA로 구현한다. */
@Repository
public class MemberPersistenceAdapter implements MemberPersistence {

	private final MemberRepository repository;

	public MemberPersistenceAdapter(MemberRepository repository) {
		this.repository = repository;
	}

	@Override
	public Member save(Member member) {
		return repository.save(member);
	}

	@Override
	public Optional<Member> findById(Long memberId) {
		return repository.findById(memberId);
	}

	@Override
	public Optional<Member> findByIdForUpdate(Long memberId) {
		return repository.findByIdForUpdate(memberId);
	}

	@Override
	public boolean existsByNormalizedNicknameAndIdNot(
		String normalizedNickname,
		Long memberId
	) {
		return repository.existsByNormalizedNicknameAndIdNot(
			normalizedNickname,
			memberId
		);
	}

	@Override
	public Member saveProfile(Member member) {
		try {
			return repository.saveAndFlush(member);
		} catch (DataIntegrityViolationException exception) {
			throw new ProfileUpdateException(
				ProfileUpdateErrorCode.NICKNAME_ALREADY_EXISTS,
				exception
			);
		}
	}
}
