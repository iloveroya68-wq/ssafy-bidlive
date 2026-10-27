package com.ssafy.bidlive.common.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import com.ssafy.bidlive.member.domain.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** BidLive JWT 발급 계약에 따라 Access Token의 서명과 claim을 검증합니다. */
@Component
public class JwtAccessTokenVerifier implements AccessTokenVerifier {

	private static final String TOKEN_TYPE_CLAIM = "tokenType";
	private static final String ROLE_CLAIM = "role";
	private static final String ACCESS_TOKEN_TYPE = "ACCESS";
	private static final String REQUIRED_ALGORITHM = "HS256";

	private final JwtParser parser;

	@Autowired
	public JwtAccessTokenVerifier(JwtProperties properties) {
		this(properties, Clock.systemUTC());
	}

	JwtAccessTokenVerifier(JwtProperties properties, Clock clock) {
		this.parser = Jwts.parser()
			.verifyWith(properties.signingKey())
			.requireIssuer(properties.issuer())
			.clock(() -> Date.from(clock.instant()))
			.build();
	}

	@Override
	public VerifiedAccessToken verify(String token) {
		if (token == null || token.isBlank()) {
			throw failure(AccessTokenFailureReason.MISSING);
		}

		try {
			Jws<Claims> parsedToken = parser.parseSignedClaims(token);
			validateAlgorithm(parsedToken);

			Claims claims = parsedToken.getPayload();
			validateTokenType(claims);

			Long memberId = memberId(claims.getSubject());
			MemberRole role = role(claims.get(ROLE_CLAIM));
			Instant expiresAt = expiration(claims.getExpiration());

			return new VerifiedAccessToken(memberId, role, expiresAt);
		} catch (ExpiredJwtException exception) {
			throw failure(AccessTokenFailureReason.EXPIRED, exception);
		} catch (SignatureException exception) {
			throw failure(AccessTokenFailureReason.INVALID_SIGNATURE, exception);
		} catch (MissingClaimException | IncorrectClaimException exception) {
			throw failure(AccessTokenFailureReason.INVALID_ISSUER, exception);
		} catch (MalformedJwtException | UnsupportedJwtException exception) {
			throw failure(AccessTokenFailureReason.MALFORMED, exception);
		} catch (JwtException | IllegalArgumentException exception) {
			throw failure(AccessTokenFailureReason.INVALID_CLAIMS, exception);
		}
	}

	private void validateAlgorithm(Jws<Claims> parsedToken) {
		if (!REQUIRED_ALGORITHM.equals(parsedToken.getHeader().getAlgorithm())) {
			throw failure(AccessTokenFailureReason.INVALID_SIGNATURE);
		}
	}

	private void validateTokenType(Claims claims) {
		Object tokenType = claims.get(TOKEN_TYPE_CLAIM);
		if (!ACCESS_TOKEN_TYPE.equals(tokenType)) {
			throw failure(AccessTokenFailureReason.INVALID_TOKEN_TYPE);
		}
	}

	private Long memberId(String subject) {
		if (subject == null || subject.isBlank()) {
			throw failure(AccessTokenFailureReason.INVALID_CLAIMS);
		}

		try {
			long memberId = Long.parseLong(subject);
			if (memberId <= 0) {
				throw failure(AccessTokenFailureReason.INVALID_CLAIMS);
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw failure(AccessTokenFailureReason.INVALID_CLAIMS, exception);
		}
	}

	private MemberRole role(Object roleClaim) {
		if (!(roleClaim instanceof String role) || role.isBlank()) {
			throw failure(AccessTokenFailureReason.INVALID_CLAIMS);
		}

		try {
			return MemberRole.valueOf(role);
		} catch (IllegalArgumentException exception) {
			throw failure(AccessTokenFailureReason.INVALID_CLAIMS, exception);
		}
	}

	private Instant expiration(Date expiration) {
		if (expiration == null) {
			throw failure(AccessTokenFailureReason.INVALID_CLAIMS);
		}
		return expiration.toInstant();
	}

	private AccessTokenVerificationException failure(AccessTokenFailureReason reason) {
		return new AccessTokenVerificationException(reason);
	}

	private AccessTokenVerificationException failure(
		AccessTokenFailureReason reason,
		Throwable cause
	) {
		return new AccessTokenVerificationException(reason, cause);
	}
}
