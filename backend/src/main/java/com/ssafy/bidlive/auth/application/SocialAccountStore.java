package com.ssafy.bidlive.auth.application;

import java.util.Optional;

import com.ssafy.bidlive.auth.domain.SocialAccount;
import com.ssafy.bidlive.auth.domain.SocialProvider;

/** 소셜 계정 조회와 저장을 영속성 기술과 분리한 Application Port다. */
public interface SocialAccountStore {

	Optional<Long> findMemberId(SocialProvider provider, String providerUserId);

	void save(SocialAccount socialAccount);
}
