package com.ssafy.bidlive.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.ssafy.bidlive.auth.domain.SocialProvider;
import com.ssafy.bidlive.member.application.OAuthMemberRegistrationService;
import com.ssafy.bidlive.wallet.application.WalletInitializationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 기존 소셜 회원 결과와 온보딩 계산 위임을 검증한다. */
@ExtendWith(MockitoExtension.class)
class OAuthMemberProvisioningServiceTest {

	@Mock
	private SocialAccountStore socialAccountStore;

	@Mock
	private OAuthMemberRegistrationService memberRegistrationService;

	@Mock
	private WalletInitializationService walletInitializationService;

	private OAuthMemberProvisioningService service;

	@BeforeEach
	void setUp() {
		service = new OAuthMemberProvisioningService(
			socialAccountStore,
			memberRegistrationService,
			walletInitializationService
		);
	}

	@Test
	void returnsExistingMemberWithCalculatedOnboardingStatus() {
		OAuthMemberProfile profile = new OAuthMemberProfile(
			SocialProvider.KAKAO,
			"kakao-existing",
			null,
			null,
			null
		);
		when(socialAccountStore.findMemberId(
			SocialProvider.KAKAO,
			"kakao-existing"
		)).thenReturn(Optional.of(25L));
		when(memberRegistrationService.isOnboardingRequired(25L)).thenReturn(false);

		OAuthMemberProvisioningService.OAuthMemberResult result =
			service.findOrCreate(profile);

		assertThat(result.memberId()).isEqualTo(25L);
		assertThat(result.isNewMember()).isFalse();
		assertThat(result.onboardingRequired()).isFalse();
		verify(memberRegistrationService).isOnboardingRequired(25L);
		verifyNoInteractions(walletInitializationService);
	}
}
