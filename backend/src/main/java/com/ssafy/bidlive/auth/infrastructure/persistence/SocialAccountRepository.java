package com.ssafy.bidlive.auth.infrastructure.persistence;

import java.util.Optional;

import com.ssafy.bidlive.auth.domain.SocialAccount;
import com.ssafy.bidlive.auth.domain.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

	Optional<SocialAccount> findByProviderAndProviderUserId(
		SocialProvider provider,
		String providerUserId
	);
}
