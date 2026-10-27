package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.application.port.out.NicknameProhibitedWordSource;
import com.ssafy.bidlive.member.domain.NicknameAvailabilityErrorCode;
import com.ssafy.bidlive.member.domain.NicknameAvailabilityReason;
import com.ssafy.bidlive.member.domain.NicknamePolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 회원이 입력한 닉네임의 형식, 금칙어 및 중복 여부를 순서대로 확인한다. */
@Service
public class NicknameAvailabilityService {

	private final MemberPersistence memberPersistence;
	private final NicknameProhibitedWordSource prohibitedWordSource;

	public NicknameAvailabilityService(
		MemberPersistence memberPersistence,
		NicknameProhibitedWordSource prohibitedWordSource
	) {
		this.memberPersistence = memberPersistence;
		this.prohibitedWordSource = prohibitedWordSource;
	}

	@Transactional(readOnly = true)
	public NicknameAvailabilityResult check(Long memberId, String nickname) {
		if (memberId == null || memberId <= 0) {
			throw new NicknameAvailabilityException(
				NicknameAvailabilityErrorCode.UNAUTHORIZED
			);
		}

		String normalizedNickname = NicknamePolicy.normalize(nickname);
		if (normalizedNickname == null || normalizedNickname.isBlank()) {
			throw new NicknameAvailabilityException(
				NicknameAvailabilityErrorCode.NICKNAME_REQUIRED
			);
		}

		if (!NicknamePolicy.isValidFormat(normalizedNickname)) {
			return unavailable(
				normalizedNickname,
				NicknameAvailabilityReason.INVALID_FORMAT
			);
		}

		if (NicknamePolicy.containsProhibitedWord(
			normalizedNickname,
			prohibitedWordSource.getActiveWords()
		)) {
			return unavailable(
				normalizedNickname,
				NicknameAvailabilityReason.PROHIBITED_WORD
			);
		}

		if (memberPersistence.existsByNormalizedNicknameAndIdNot(
			NicknamePolicy.normalizedKey(normalizedNickname),
			memberId
		)) {
			return unavailable(
				normalizedNickname,
				NicknameAvailabilityReason.ALREADY_EXISTS
			);
		}

		return new NicknameAvailabilityResult(normalizedNickname, true, null);
	}

	private NicknameAvailabilityResult unavailable(
		String nickname,
		NicknameAvailabilityReason reason
	) {
		return new NicknameAvailabilityResult(nickname, false, reason);
	}
}
