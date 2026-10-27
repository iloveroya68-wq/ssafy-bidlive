package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.application.port.out.NicknameProhibitedWordSource;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.NicknamePolicy;
import com.ssafy.bidlive.member.domain.ProfileUpdateErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 회원의 닉네임을 저장하고 최신 필수 약관을 포함한 온보딩 상태를 계산한다. */
@Service
public class ProfileUpdateService {

	private final MemberPersistence memberPersistence;
	private final NicknameProhibitedWordSource prohibitedWordSource;
	private final OnboardingStatusService onboardingStatusService;

	public ProfileUpdateService(
		MemberPersistence memberPersistence,
		NicknameProhibitedWordSource prohibitedWordSource,
		OnboardingStatusService onboardingStatusService
	) {
		this.memberPersistence = memberPersistence;
		this.prohibitedWordSource = prohibitedWordSource;
		this.onboardingStatusService = onboardingStatusService;
	}

	@Transactional
	public ProfileUpdateResult update(Long memberId, ProfileUpdateCommand command) {
		validateMemberId(memberId);
		if (command == null || command.nickname() == null) {
			throw error(ProfileUpdateErrorCode.EMPTY_UPDATE_REQUEST);
		}

		String nickname = NicknamePolicy.normalize(command.nickname());
		validateNickname(nickname);

		Member member = memberPersistence.findByIdForUpdate(memberId)
			.orElseThrow(() -> error(ProfileUpdateErrorCode.MEMBER_NOT_FOUND));
		String normalizedNickname = NicknamePolicy.normalizedKey(nickname);
		if (memberPersistence.existsByNormalizedNicknameAndIdNot(
			normalizedNickname,
			memberId
		)) {
			throw error(ProfileUpdateErrorCode.NICKNAME_ALREADY_EXISTS);
		}

		member.changeNickname(nickname);
		Member savedMember = memberPersistence.saveProfile(member);

		return new ProfileUpdateResult(
			savedMember.getId(),
			savedMember.getNickname(),
			onboardingStatusService.isOnboardingRequired(savedMember),
			savedMember.getUpdatedAt()
		);
	}

	private void validateMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw error(ProfileUpdateErrorCode.UNAUTHORIZED);
		}
	}

	private void validateNickname(String nickname) {
		if (!NicknamePolicy.isValidFormat(nickname)
			|| NicknamePolicy.containsProhibitedWord(
				nickname,
				prohibitedWordSource.getActiveWords()
			)) {
			throw error(ProfileUpdateErrorCode.INVALID_NICKNAME);
		}
	}

	private ProfileUpdateException error(ProfileUpdateErrorCode errorCode) {
		return new ProfileUpdateException(errorCode);
	}
}
