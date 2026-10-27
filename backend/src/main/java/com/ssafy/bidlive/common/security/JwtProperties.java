package com.ssafy.bidlive.common.security;

import java.time.Duration;

import javax.crypto.SecretKey;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
	String issuer,
	String secretBase64,
	Duration accessTokenTtl,
	Duration refreshTokenTtl
) {

	private static final int MINIMUM_SECRET_BYTES = 32;

	public JwtProperties {
		if (issuer == null || issuer.isBlank()) {
			throw new IllegalArgumentException("JWT issuer must not be blank");
		}
		if (secretBase64 == null || secretBase64.isBlank()) {
			throw new IllegalArgumentException("JWT secret must not be blank");
		}
		if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
			throw new IllegalArgumentException("Access token TTL must be positive");
		}
		if (refreshTokenTtl == null || refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
			throw new IllegalArgumentException("Refresh token TTL must be positive");
		}

		byte[] secret = decodeSecret(secretBase64);
		if (secret.length < MINIMUM_SECRET_BYTES) {
			throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
		}
	}

	public SecretKey signingKey() {
		return Keys.hmacShaKeyFor(decodeSecret(secretBase64));
	}

	private static byte[] decodeSecret(String secretBase64) {
		try {
			return Decoders.BASE64.decode(secretBase64);
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException("JWT secret must be valid Base64", exception);
		}
	}
}
