package com.ssafy.bidlive.auth.infrastructure.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.client.ResourceAccessException;

class KakaoOAuth2AuthenticationFailureHandlerTest {

	private KakaoOAuth2AuthenticationFailureHandler handler;

	@BeforeEach
	void setUp() {
		handler = new KakaoOAuth2AuthenticationFailureHandler(
			new OAuth2CallbackProperties(
				URI.create("http://localhost:5173/oauth/callback"),
				Duration.ofMinutes(2)
			)
		);
	}

	@Test
	void redirectsMissingStateAsInvalidOAuthState() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationFailure(
			request,
			response,
			oauthException("authorization_request_not_found")
		);

		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:5173/oauth/callback?error=INVALID_OAUTH_STATE");
	}

	@Test
	void redirectsUserDenialAsAccessDenied() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("state", "valid-state");
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationFailure(request, response, oauthException("access_denied"));

		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:5173/oauth/callback?error=OAUTH_ACCESS_DENIED");
	}

	@Test
	void redirectsDenialWithoutStateAsInvalidState() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationFailure(request, response, oauthException("access_denied"));

		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:5173/oauth/callback?error=INVALID_OAUTH_STATE");
	}

	@Test
	void redirectsProviderConnectionFailureAsUnavailable() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("state", "valid-state");
		MockHttpServletResponse response = new MockHttpServletResponse();
		OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
			new OAuth2Error("invalid_token_response"),
			new ResourceAccessException("connection failed", new ConnectException())
		);

		handler.onAuthenticationFailure(request, response, exception);

		assertThat(response.getRedirectedUrl())
			.isEqualTo(
				"http://localhost:5173/oauth/callback?error=OAUTH_PROVIDER_UNAVAILABLE"
			);
	}

	private OAuth2AuthenticationException oauthException(String code) {
		return new OAuth2AuthenticationException(new OAuth2Error(code));
	}
}
