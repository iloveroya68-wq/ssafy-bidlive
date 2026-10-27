package com.ssafy.bidlive.auth.infrastructure.oauth2;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Set;

import com.ssafy.bidlive.auth.domain.OAuth2CallbackErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KakaoOAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

	private static final Set<String> STATE_ERROR_CODES = Set.of(
		"authorization_request_not_found",
		"invalid_state_parameter"
	);

	private final OAuth2CallbackProperties properties;

	public KakaoOAuth2AuthenticationFailureHandler(OAuth2CallbackProperties properties) {
		this.properties = properties;
	}

	@Override
	public void onAuthenticationFailure(
		HttpServletRequest request,
		HttpServletResponse response,
		AuthenticationException exception
	) throws IOException {
		OAuth2CallbackErrorCode errorCode = classify(request, exception);
		String redirectUri = UriComponentsBuilder.fromUri(properties.frontendCallbackUri())
			.queryParam("error", errorCode.name())
			.build()
			.encode()
			.toUriString();

		OAuth2SessionCleaner.clear(request);
		response.sendRedirect(redirectUri);
	}

	private OAuth2CallbackErrorCode classify(
		HttpServletRequest request,
		AuthenticationException exception
	) {
		String oauthErrorCode = oauthErrorCode(exception);
		if (request.getParameter("state") == null) {
			return OAuth2CallbackErrorCode.INVALID_OAUTH_STATE;
		}
		if ("access_denied".equals(oauthErrorCode)) {
			return OAuth2CallbackErrorCode.OAUTH_ACCESS_DENIED;
		}
		if (STATE_ERROR_CODES.contains(oauthErrorCode)) {
			return OAuth2CallbackErrorCode.INVALID_OAUTH_STATE;
		}
		if (isProviderUnavailable(exception)) {
			return OAuth2CallbackErrorCode.OAUTH_PROVIDER_UNAVAILABLE;
		}
		return OAuth2CallbackErrorCode.OAUTH_AUTHENTICATION_FAILED;
	}

	private String oauthErrorCode(AuthenticationException exception) {
		if (exception instanceof OAuth2AuthenticationException oauthException) {
			return oauthException.getError().getErrorCode();
		}
		return "";
	}

	private boolean isProviderUnavailable(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof ResourceAccessException
				|| current instanceof ConnectException
				|| current instanceof SocketTimeoutException
				|| current instanceof UnknownHostException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}
