package com.ssafy.bidlive.auth.infrastructure.oauth2;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/**
 * Applies the API error contract to failures raised while Spring Security creates an
 * OAuth authorization request.
 */
@Component
public class OAuth2AuthorizationRequestRedirectFilterCustomizer implements BeanPostProcessor {

	private static final String INVALID_REQUEST_BODY = """
		{"code":"OAUTH_REQUEST_INVALID","message":"OAuth 요청 설정이 올바르지 않습니다."}
		""";

	private static final String PROVIDER_UNAVAILABLE_BODY = """
		{"code":"OAUTH_PROVIDER_UNAVAILABLE","message":"카카오 인증 서버에 연결할 수 없습니다."}
		""";

	@Override
	public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName) {
		if (bean instanceof OAuth2AuthorizationRequestRedirectFilter filter) {
			filter.setAuthenticationFailureHandler(this::handleAuthorizationFailure);
		}
		return bean;
	}

	private void handleAuthorizationFailure(
		HttpServletRequest request,
		HttpServletResponse response,
		AuthenticationException exception
	) throws IOException {
		boolean providerUnavailable = isProviderUnavailable(exception);
		HttpStatus status = providerUnavailable
			? HttpStatus.SERVICE_UNAVAILABLE
			: HttpStatus.BAD_REQUEST;
		String body = providerUnavailable
			? PROVIDER_UNAVAILABLE_BODY
			: INVALID_REQUEST_BODY;

		response.setStatus(status.value());
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(body);
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
