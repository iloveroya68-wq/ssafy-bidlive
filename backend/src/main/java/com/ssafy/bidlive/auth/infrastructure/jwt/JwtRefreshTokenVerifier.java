package com.ssafy.bidlive.auth.infrastructure.jwt;

import java.util.regex.Pattern;

import com.ssafy.bidlive.auth.application.RefreshTokenVerificationException;
import com.ssafy.bidlive.auth.application.RefreshTokenVerifier;
import com.ssafy.bidlive.auth.application.VerifiedRefreshToken;
import com.ssafy.bidlive.common.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

/** 자체 JWT의 서명·만료·발급자와 Refresh Token 전용 클레임을 검증한다. */
@Component
public class JwtRefreshTokenVerifier implements RefreshTokenVerifier {

	private static final String TOKEN_TYPE_CLAIM = "tokenType";
	private static final String REFRESH_TOKEN_TYPE = "REFRESH";
	private static final Pattern TOKEN_ID_PATTERN =
		Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

	private final JwtProperties properties;

	public JwtRefreshTokenVerifier(JwtProperties properties) {
		this.properties = properties;
	}

	@Override
	public VerifiedRefreshToken verify(String refreshToken) {
		try {
			return toVerifiedToken(parseClaims(refreshToken));
		} catch (ExpiredJwtException exception) {
			throw new RefreshTokenVerificationException(
				RefreshTokenVerificationException.Reason.EXPIRED,
				exception
			);
		} catch (JwtException | IllegalArgumentException exception) {
			throw new RefreshTokenVerificationException(
				RefreshTokenVerificationException.Reason.INVALID,
				exception
			);
		}
	}

	@Override
	public VerifiedRefreshToken identify(String refreshToken) {
		try {
			return toVerifiedToken(parseClaims(refreshToken));
		} catch (ExpiredJwtException exception) {
			try {
				return toVerifiedToken(exception.getClaims());
			} catch (IllegalArgumentException claimsException) {
				throw invalid(claimsException);
			}
		} catch (JwtException | IllegalArgumentException exception) {
			throw invalid(exception);
		}
	}

	private Claims parseClaims(String refreshToken) {
		return Jwts.parser()
			.verifyWith(properties.signingKey())
			.requireIssuer(properties.issuer())
			.require(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
			.build()
			.parseSignedClaims(refreshToken)
			.getPayload();
	}

	private VerifiedRefreshToken toVerifiedToken(Claims claims) {
		if (!properties.issuer().equals(claims.getIssuer())) {
			throw new IllegalArgumentException("Refresh token issuer is invalid");
		}
		if (!REFRESH_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
			throw new IllegalArgumentException("Refresh token type is invalid");
		}

		Long memberId = Long.valueOf(claims.getSubject());
		String tokenId = claims.getId();
		if (tokenId == null || !TOKEN_ID_PATTERN.matcher(tokenId).matches()) {
			throw new IllegalArgumentException("Refresh token ID is invalid");
		}
		return new VerifiedRefreshToken(memberId, tokenId);
	}

	private RefreshTokenVerificationException invalid(Throwable cause) {
		return new RefreshTokenVerificationException(
			RefreshTokenVerificationException.Reason.INVALID,
			cause
		);
	}
}
