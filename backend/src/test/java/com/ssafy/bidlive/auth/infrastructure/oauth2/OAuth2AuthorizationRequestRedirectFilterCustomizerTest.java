package com.ssafy.bidlive.auth.infrastructure.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.client.ResourceAccessException;

class OAuth2AuthorizationRequestRedirectFilterCustomizerTest {

	@Test
	void returnsServiceUnavailableWhenProviderCannotBeReached() throws Exception {
		OAuth2AuthorizationRequestResolver unavailableProviderResolver =
			new OAuth2AuthorizationRequestResolver() {
				@Override
				public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
					throw new ResourceAccessException("Kakao authorization server is unavailable");
				}

				@Override
				public OAuth2AuthorizationRequest resolve(
					HttpServletRequest request,
					String clientRegistrationId
				) {
					throw new ResourceAccessException("Kakao authorization server is unavailable");
				}
			};

		var filter = new OAuth2AuthorizationRequestRedirectFilter(unavailableProviderResolver);
		new OAuth2AuthorizationRequestRedirectFilterCustomizer()
			.postProcessBeforeInitialization(filter, "oauth2AuthorizationRequestRedirectFilter");

		var request = new MockHttpServletRequest("GET", "/oauth2/authorization/kakao");
		var response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(response.getContentAsString()).contains("OAUTH_PROVIDER_UNAVAILABLE");
	}
}
