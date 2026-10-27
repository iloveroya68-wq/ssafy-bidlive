package com.ssafy.bidlive.auth.application;

import com.ssafy.bidlive.auth.domain.SocialAccount;
import com.ssafy.bidlive.member.application.OAuthMemberRegistrationService;
import com.ssafy.bidlive.member.application.OAuthMemberRegistrationService.OAuthMemberRegistration;
import com.ssafy.bidlive.wallet.application.WalletInitializationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** OAuth 프로필을 기존 회원과 연결하거나 신규 회원과 기본 지갑을 생성한다. */
@Service
public class OAuthMemberProvisioningService {

	private final SocialAccountStore socialAccountStore;
	private final OAuthMemberRegistrationService memberRegistrationService;
	private final WalletInitializationService walletInitializationService;

	public OAuthMemberProvisioningService(
		SocialAccountStore socialAccountStore,
		OAuthMemberRegistrationService memberRegistrationService,
		WalletInitializationService walletInitializationService
	) {
		this.socialAccountStore = socialAccountStore;
		this.memberRegistrationService = memberRegistrationService;
		this.walletInitializationService = walletInitializationService;
	}

	@Transactional
	public OAuthMemberResult findOrCreate(OAuthMemberProfile profile) {
		validate(profile);

		return socialAccountStore.findMemberId(profile.provider(), profile.providerUserId())
			.map(this::existingMember)
			.orElseGet(() -> createMember(profile));
	}

	private OAuthMemberResult existingMember(Long memberId) {
		boolean onboardingRequired =
			memberRegistrationService.isOnboardingRequired(memberId);
		return new OAuthMemberResult(memberId, false, onboardingRequired);
	}

	private OAuthMemberResult createMember(OAuthMemberProfile profile) {
		try {
			OAuthMemberRegistration member = memberRegistrationService.register();
			socialAccountStore.save(SocialAccount.connect(
				member.memberId(),
				profile.provider(),
				profile.providerUserId(),
				profile.email(),
				profile.nickname(),
				profile.profileImageUrl()
			));
			walletInitializationService.initializeFor(member.memberId());

			return new OAuthMemberResult(
				member.memberId(),
				true,
				member.onboardingRequired()
			);
		} catch (OAuthMemberProvisioningException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw OAuthMemberProvisioningException.memberCreationFailed(exception);
		}
	}

	private void validate(OAuthMemberProfile profile) {
		if (profile == null
			|| !profile.hasRequiredIdentity()
			|| !profile.fitsPersistenceLimits()) {
			throw OAuthMemberProvisioningException.profileInsufficient();
		}
	}

	public record OAuthMemberResult(
		Long memberId,
		boolean isNewMember,
		boolean onboardingRequired
	) {
	}
}
