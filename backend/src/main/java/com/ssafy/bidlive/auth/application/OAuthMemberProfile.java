package com.ssafy.bidlive.auth.application;

import com.ssafy.bidlive.auth.domain.SocialProvider;

/**
 * OAuth 제공자가 전달한 회원 식별자와 선택 프로필 정보를 가입 유스케이스에 전달한다.
 */
public record OAuthMemberProfile(
	SocialProvider provider,
	String providerUserId,
	String email,
	String nickname,
	String profileImageUrl
) {

	private static final int PROVIDER_USER_ID_MAX_LENGTH = 255;
	private static final int EMAIL_MAX_LENGTH = 320;
	private static final int NICKNAME_MAX_LENGTH = 100;
	private static final int PROFILE_IMAGE_URL_MAX_LENGTH = 2048;

	public OAuthMemberProfile {
		providerUserId = normalize(providerUserId);
		email = normalize(email);
		nickname = normalize(nickname);
		profileImageUrl = normalize(profileImageUrl);
	}

	public boolean hasRequiredIdentity() {
		return provider != null && providerUserId != null;
	}

	public boolean fitsPersistenceLimits() {
		return withinLimit(providerUserId, PROVIDER_USER_ID_MAX_LENGTH)
			&& withinLimit(email, EMAIL_MAX_LENGTH)
			&& withinLimit(nickname, NICKNAME_MAX_LENGTH)
			&& withinLimit(profileImageUrl, PROFILE_IMAGE_URL_MAX_LENGTH);
	}

	private static String normalize(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private static boolean withinLimit(String value, int maxLength) {
		return value == null || value.length() <= maxLength;
	}
}
