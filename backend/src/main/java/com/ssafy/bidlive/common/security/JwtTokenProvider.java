package com.ssafy.bidlive.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.ssafy.bidlive.member.domain.Member;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 자체 Access·Refresh Token을 발급하고 Access Token의 서명과 전용 클레임을 검증한다. */
@Component
public class JwtTokenProvider {

	private static final String TOKEN_TYPE_CLAIM = "tokenType";
	private static final String ROLE_CLAIM = "role";
	private static final String ACCESS_TOKEN_TYPE = "ACCESS";
	private static final String REFRESH_TOKEN_TYPE = "REFRESH";

	private final JwtProperties properties;
	private final Clock clock;

	@Autowired
	public JwtTokenProvider(JwtProperties properties) {
		this(properties, Clock.systemUTC());
	}

	JwtTokenProvider(JwtProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	public IssuedTokens issue(Member member) {
		Instant issuedAt = clock.instant();
		String memberId = member.getId().toString();

		String accessToken = Jwts.builder()
			.issuer(properties.issuer())
			.subject(memberId)
			.id(UUID.randomUUID().toString())
			.issuedAt(Date.from(issuedAt))
			.expiration(Date.from(issuedAt.plus(properties.accessTokenTtl())))
			.claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
			.claim(ROLE_CLAIM, member.getRole().name())
			.signWith(properties.signingKey(), Jwts.SIG.HS256)
			.compact();

		String refreshToken = Jwts.builder()
			.issuer(properties.issuer())
			.subject(memberId)
			.id(UUID.randomUUID().toString())
			.issuedAt(Date.from(issuedAt))
			.expiration(Date.from(issuedAt.plus(properties.refreshTokenTtl())))
			.claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
			.signWith(properties.signingKey(), Jwts.SIG.HS256)
			.compact();

		return new IssuedTokens(
			accessToken,
			refreshToken,
			properties.accessTokenTtl(),
			properties.refreshTokenTtl()
		);
	}

	public VerifiedAccessToken verifyAccessToken(String accessToken) {
		if (accessToken == null || accessToken.isBlank()) {
			throw new IllegalArgumentException("Access token must not be blank");
		}

		try {
			Claims claims = Jwts.parser()
				.verifyWith(properties.signingKey())
				.requireIssuer(properties.issuer())
				.require(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
				.build()
				.parseSignedClaims(accessToken)
				.getPayload();

			Long memberId = Long.valueOf(claims.getSubject());
			String role = claims.get(ROLE_CLAIM, String.class);
			if (memberId <= 0 || role == null || role.isBlank()) {
				throw new IllegalArgumentException("Access token claims are invalid");
			}
			return new VerifiedAccessToken(memberId, role);
		} catch (JwtException | NumberFormatException exception) {
			throw new IllegalArgumentException("Access token is invalid", exception);
		}
	}

	public record IssuedTokens(
		String accessToken,
		String refreshToken,
		Duration accessTokenTtl,
		Duration refreshTokenTtl
	) {
	}

	public record VerifiedAccessToken(
		Long memberId,
		String role
	) {
	}
}
