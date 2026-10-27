package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.Member;
import org.springframework.stereotype.Service;

/** OAuth 가입용 회원 생성과 온보딩 상태 조회를 제공한다. */
@Service
public class OAuthMemberRegistrationService {

	private final MemberPersistence memberPersistence;

	public OAuthMemberRegistrationService(MemberPersistence memberPersistence) {
		this.memberPersistence = memberPersistence;
	}

	public OAuthMemberRegistration register() {
		Member member = memberPersistence.save(Member.createOAuthMember());
		return new OAuthMemberRegistration(member.getId(), isOnboardingRequired(member));
	}

	public boolean isOnboardingRequired(Long memberId) {
		Member member = memberPersistence.findById(memberId)
			.orElseThrow(() -> new IllegalStateException(
				"Social account member does not exist: " + memberId
			));
		return isOnboardingRequired(member);
	}

	private boolean isOnboardingRequired(Member member) {
		return member.getNickname() == null || member.getNickname().isBlank();
	}

	public record OAuthMemberRegistration(Long memberId, boolean onboardingRequired) {
	}
}
