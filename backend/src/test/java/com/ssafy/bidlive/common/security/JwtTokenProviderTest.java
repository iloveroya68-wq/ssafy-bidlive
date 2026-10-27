package com.ssafy.bidlive.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

/** 자체 JWT 발급과 Access Token 전용 검증 규칙을 검증한다. */
class JwtTokenProviderTest {

	private static final String SECRET =
		"dGVzdC1qd3Qtc2VjcmV0LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZw==";

	@Test
	void issuesSignedAccessAndRefreshTokensWithExpectedClaims() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		JwtProperties properties = new JwtProperties(
			"bidlive-test",
			SECRET,
			Duration.ofMinutes(30),
			Duration.ofDays(14)
		);
		JwtTokenProvider provider = new JwtTokenProvider(
			properties,
			Clock.fixed(now, ZoneOffset.UTC)
		);
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(25L);
		when(member.getRole()).thenReturn(MemberRole.ADMIN);

		JwtTokenProvider.IssuedTokens tokens = provider.issue(member);

		Claims accessClaims = claims(tokens.accessToken(), properties);
		assertThat(accessClaims.getSubject()).isEqualTo("25");
		assertThat(accessClaims.get("tokenType", String.class)).isEqualTo("ACCESS");
		assertThat(accessClaims.get("role", String.class)).isEqualTo("ADMIN");
		assertThat(accessClaims.getExpiration().toInstant())
			.isEqualTo(now.plus(Duration.ofMinutes(30)));

		Claims refreshClaims = claims(tokens.refreshToken(), properties);
		assertThat(refreshClaims.getSubject()).isEqualTo("25");
		assertThat(refreshClaims.get("tokenType", String.class)).isEqualTo("REFRESH");
		assertThat(refreshClaims).doesNotContainKey("role");

		JwtTokenProvider.VerifiedAccessToken verifiedAccessToken =
			provider.verifyAccessToken(tokens.accessToken());
		assertThat(verifiedAccessToken.memberId()).isEqualTo(25L);
		assertThat(verifiedAccessToken.role()).isEqualTo("ADMIN");
		assertThatThrownBy(() -> provider.verifyAccessToken(tokens.refreshToken()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(refreshClaims.getExpiration().toInstant())
			.isEqualTo(now.plus(Duration.ofDays(14)));
	}

	private Claims claims(String token, JwtProperties properties) {
		return Jwts.parser()
			.verifyWith(properties.signingKey())
			.requireIssuer(properties.issuer())
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}
}
