package com.ssafy.bidlive.auth.infrastructure.persistence;

import java.util.Optional;

import com.ssafy.bidlive.auth.application.OAuthMemberProvisioningException;
import com.ssafy.bidlive.auth.application.SocialAccountStore;
import com.ssafy.bidlive.auth.domain.SocialAccount;
import com.ssafy.bidlive.auth.domain.SocialProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** SocialAccountStore 계약을 Spring Data JPA로 구현한다. */
@Repository
public class SocialAccountPersistenceAdapter implements SocialAccountStore {

	private final SocialAccountRepository repository;

	public SocialAccountPersistenceAdapter(SocialAccountRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<Long> findMemberId(SocialProvider provider, String providerUserId) {
		return repository.findByProviderAndProviderUserId(provider, providerUserId)
			.map(SocialAccount::getMemberId);
	}

	@Override
	public void save(SocialAccount socialAccount) {
		try {
			repository.saveAndFlush(socialAccount);
		} catch (DataIntegrityViolationException exception) {
			throw OAuthMemberProvisioningException.socialAccountAlreadyExists(exception);
		}
	}
}
