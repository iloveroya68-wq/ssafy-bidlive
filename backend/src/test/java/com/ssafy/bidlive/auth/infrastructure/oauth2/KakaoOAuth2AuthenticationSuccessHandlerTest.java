package com.ssafy.bidlive.auth.infrastructure.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.ssafy.bidlive.auth.application.OAuthMemberProfile;
import com.ssafy.bidlive.auth.application.OAuthMemberProvisioningException;
import com.ssafy.bidlive.auth.application.OAuthMemberProvisioningService;
import com.ssafy.bidlive.auth.application.OneTimeLoginCodeService;
import com.ssafy.bidlive.auth.domain.SocialProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/** 카카오 성공 프로필 전달과 Callback 성공·실패 Redirect를 검증한다. */
@ExtendWith(MockitoExtension.class)
class KakaoOAuth2AuthenticationSuccessHandlerTest {

	@Mock
	private OAuthMemberProvisioningService provisioningService;

	@Mock
	private OneTimeLoginCodeService loginCodeService;

	private KakaoOAuth2AuthenticationSuccessHandler handler;

	@BeforeEach
	void setUp() {
		OAuth2CallbackProperties properties = new OAuth2CallbackProperties(
			URI.create("http://localhost:5173/oauth/callback"),
			Duration.ofMinutes(2)
		);
		handler = new KakaoOAuth2AuthenticationSuccessHandler(
			provisioningService,
			loginCodeService,
			properties
		);
	}

	@Test
	void provisionsMemberAndRedirectsWithOnlyServiceLoginCode() throws Exception {
		when(provisioningService.findOrCreate(any(OAuthMemberProfile.class)))
			.thenReturn(new OAuthMemberProvisioningService.OAuthMemberResult(
				10L,
				true,
				true
			));
		when(loginCodeService.issue(10L, Duration.ofMinutes(2)))
			.thenReturn("service-login-code");
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(
			requestWithSession(),
			response,
			kakaoAuthentication(123456789L)
		);

		assertThat(response.getStatus()).isEqualTo(302);
		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:5173/oauth/callback?code=service-login-code")
			.doesNotContain("access_token", "refresh_token");
		verify(provisioningService).findOrCreate(new OAuthMemberProfile(
			SocialProvider.KAKAO,
			"123456789",
			"member@example.com",
			"카카오 닉네임",
			"https://example.com/profile.png"
		));
	}

	@Test
	void redirectsSocialAccountConflictAsServiceErrorCode() throws Exception {
		when(provisioningService.findOrCreate(any(OAuthMemberProfile.class)))
			.thenThrow(OAuthMemberProvisioningException.socialAccountAlreadyExists(
				new IllegalStateException("duplicate")
			));
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(
			requestWithSession(),
			response,
			kakaoAuthentication(123456789L)
		);

		assertThat(response.getRedirectedUrl())
			.isEqualTo(
				"http://localhost:5173/oauth/callback?error=SOCIAL_ACCOUNT_CONFLICT"
			);
	}

	@Test
	void redirectsInsufficientProfileAsAuthenticationFailure() throws Exception {
		when(provisioningService.findOrCreate(any(OAuthMemberProfile.class)))
			.thenThrow(OAuthMemberProvisioningException.profileInsufficient());
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(
			requestWithSession(),
			response,
			kakaoAuthentication(" ")
		);

		assertThat(response.getRedirectedUrl())
			.isEqualTo(
				"http://localhost:5173/oauth/callback?error=OAUTH_AUTHENTICATION_FAILED"
			);
	}

	private MockHttpServletRequest requestWithSession() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession();
		return request;
	}

	private OAuth2AuthenticationToken kakaoAuthentication(Object providerUserId) {
		DefaultOAuth2User principal = new DefaultOAuth2User(
			List.of(new SimpleGrantedAuthority("ROLE_USER")),
			Map.of(
				"id", providerUserId,
				"kakao_account", Map.of(
					"email", "member@example.com",
					"profile", Map.of(
						"nickname", "카카오 닉네임",
						"profile_image_url", "https://example.com/profile.png"
					)
				)
			),
			"id"
		);
		return new OAuth2AuthenticationToken(
			principal,
			principal.getAuthorities(),
			"kakao"
		);
	}
}
