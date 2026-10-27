package com.ssafy.bidlive.auth.infrastructure.oauth2;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth2")
public record OAuth2CallbackProperties(
	URI frontendCallbackUri,
	Duration loginCodeTtl
) {
	public OAuth2CallbackProperties {
		if (frontendCallbackUri == null || !frontendCallbackUri.isAbsolute()) {
			throw new IllegalArgumentException("app.oauth2.frontend-callback-uri must be absolute");
		}
		if (loginCodeTtl == null || loginCodeTtl.isZero() || loginCodeTtl.isNegative()) {
			throw new IllegalArgumentException("app.oauth2.login-code-ttl must be positive");
		}
	}
}
