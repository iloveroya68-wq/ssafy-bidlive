package com.ssafy.bidlive.auth.infrastructure.oauth2;

import java.io.IOException;
import java.util.Map;

import com.ssafy.bidlive.auth.application.OAuthMemberProfile;
import com.ssafy.bidlive.auth.application.OAuthMemberProvisioningException;
import com.ssafy.bidlive.auth.application.OAuthMemberProvisioningService;
import com.ssafy.bidlive.auth.application.OneTimeLoginCodeService;
import com.ssafy.bidlive.auth.domain.OAuth2CallbackErrorCode;
import com.ssafy.bidlive.auth.domain.OAuthMemberProvisioningErrorCode;
import com.ssafy.bidlive.auth.domain.SocialProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** 카카오 인증 성공 후 회원을 연결하고 서비스 로그인 코드를 발급한다. */
@Component
public class KakaoOAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	private static final String KAKAO_REGISTRATION_ID = "kakao";

	private final OAuthMemberProvisioningService memberProvisioningService;
	private final OneTimeLoginCodeService loginCodeService;
	private final OAuth2CallbackProperties properties;

	public KakaoOAuth2AuthenticationSuccessHandler(
		OAuthMemberProvisioningService memberProvisioningService,
		OneTimeLoginCodeService loginCodeService,
		OAuth2CallbackProperties properties
	) {
		this.memberProvisioningService = memberProvisioningService;
		this.loginCodeService = loginCodeService;
		this.properties = properties;
	}

	@Override
	public void onAuthenticationSuccess(
		HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication
	) throws IOException {
		OAuthMemberProfile profile;
		try {
			profile = extractKakaoProfile(authentication);
		} catch (IllegalArgumentException exception) {
			clearSessionAndRedirect(
				request,
				response,
				failureUri(OAuth2CallbackErrorCode.OAUTH_AUTHENTICATION_FAILED)
			);
			return;
		}

		try {
			Long memberId = memberProvisioningService
				.findOrCreate(profile)
				.memberId();
			String loginCode = loginCodeService.issue(memberId, properties.loginCodeTtl());

			clearSessionAndRedirect(request, response, successUri(loginCode));
		} catch (OAuthMemberProvisioningException exception) {
			clearSessionAndRedirect(
				request,
				response,
				failureUri(toCallbackErrorCode(exception.getErrorCode()))
			);
		} catch (RuntimeException exception) {
			clearSessionAndRedirect(
				request,
				response,
				failureUri(OAuth2CallbackErrorCode.OAUTH_MEMBER_CREATION_FAILED)
			);
		}
	}

	private OAuthMemberProfile extractKakaoProfile(Authentication authentication) {
		if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)
			|| !KAKAO_REGISTRATION_ID.equals(oauthToken.getAuthorizedClientRegistrationId())) {
			throw new IllegalArgumentException("Unsupported OAuth provider");
		}

		OAuth2User principal = oauthToken.getPrincipal();
		Map<String, Object> attributes = principal.getAttributes();
		Map<?, ?> kakaoAccount = nestedMap(attributes.get("kakao_account"));
		Map<?, ?> profile = nestedMap(kakaoAccount.get("profile"));
		Map<?, ?> properties = nestedMap(attributes.get("properties"));

		return new OAuthMemberProfile(
			SocialProvider.KAKAO,
			stringValue(attributes.get("id")),
			stringValue(kakaoAccount.get("email")),
			firstPresent(
				stringValue(profile.get("nickname")),
				stringValue(properties.get("nickname"))
			),
			firstPresent(
				stringValue(profile.get("profile_image_url")),
				stringValue(properties.get("profile_image"))
			)
		);
	}

	private Map<?, ?> nestedMap(Object value) {
		if (value instanceof Map<?, ?> map) {
			return map;
		}
		return Map.of();
	}

	private String stringValue(Object value) {
		return value == null ? null : value.toString();
	}

	private String firstPresent(String first, String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		return second;
	}

	private OAuth2CallbackErrorCode toCallbackErrorCode(
		OAuthMemberProvisioningErrorCode errorCode
	) {
		return switch (errorCode) {
			case SOCIAL_ACCOUNT_ALREADY_EXISTS ->
				OAuth2CallbackErrorCode.SOCIAL_ACCOUNT_CONFLICT;
			case OAUTH_PROFILE_INSUFFICIENT ->
				OAuth2CallbackErrorCode.OAUTH_AUTHENTICATION_FAILED;
			case MEMBER_CREATION_FAILED ->
				OAuth2CallbackErrorCode.OAUTH_MEMBER_CREATION_FAILED;
		};
	}

	private String successUri(String loginCode) {
		return UriComponentsBuilder.fromUri(properties.frontendCallbackUri())
			.queryParam("code", loginCode)
			.build()
			.encode()
			.toUriString();
	}

	private String failureUri(OAuth2CallbackErrorCode errorCode) {
		return UriComponentsBuilder.fromUri(properties.frontendCallbackUri())
			.queryParam("error", errorCode.name())
			.build()
			.encode()
			.toUriString();
	}

	private void clearSessionAndRedirect(
		HttpServletRequest request,
		HttpServletResponse response,
		String redirectUri
	) throws IOException {
		OAuth2SessionCleaner.clear(request);
		response.sendRedirect(redirectUri);
	}
}
