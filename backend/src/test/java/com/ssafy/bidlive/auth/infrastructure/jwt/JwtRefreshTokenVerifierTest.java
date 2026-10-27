package com.ssafy.bidlive.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import com.ssafy.bidlive.auth.application.RefreshTokenVerificationException;
import com.ssafy.bidlive.auth.application.VerifiedRefreshToken;
import com.ssafy.bidlive.common.security.JwtProperties;
import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Refresh Token JWT의 서명·만료·타입과 식별 클레임 검증을 확인한다. */
class JwtRefreshTokenVerifierTest {

	private static final String SECRET =
		"dGVzdC1qd3Qtc2VjcmV0LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZw==";

	private JwtProperties properties;
	private JwtRefreshTokenVerifier verifier;

	@BeforeEach
	void setUp() {
		properties = new JwtProperties(
			"bidlive-test",
			SECRET,
			Duration.ofMinutes(30),
			Duration.ofDays(14)
		);
		verifier = new JwtRefreshTokenVerifier(properties);
	}

	@Test
	void verifiesRefreshTokenAndReturnsMemberAndTokenIdentifiers() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(25L);
		when(member.getRole()).thenReturn(MemberRole.USER);
		String refreshToken = new JwtTokenProvider(properties)
			.issue(member)
			.refreshToken();

		VerifiedRefreshToken verified = verifier.verify(refreshToken);

		assertThat(verified.memberId()).isEqualTo(25L);
		assertThat(verified.tokenId()).isNotBlank();
	}

	@Test
	void rejectsAccessTokenAsRefreshToken() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(25L);
		when(member.getRole()).thenReturn(MemberRole.USER);
		String accessToken = new JwtTokenProvider(properties)
			.issue(member)
			.accessToken();

		assertReason(accessToken, RefreshTokenVerificationException.Reason.INVALID);
	}

	@Test
	void distinguishesExpiredRefreshToken() {
		Instant now = Instant.now();
		String expiredToken = Jwts.builder()
			.issuer(properties.issuer())
			.subject("25")
			.id("expired-id")
			.issuedAt(Date.from(now.minus(Duration.ofHours(2))))
			.expiration(Date.from(now.minus(Duration.ofHours(1))))
			.claim("tokenType", "REFRESH")
			.signWith(properties.signingKey(), Jwts.SIG.HS256)
			.compact();

		assertReason(expiredToken, RefreshTokenVerificationException.Reason.EXPIRED);
	}

	@Test
	void identifiesExpiredRefreshTokenForLogout() {
		Instant now = Instant.now();
		String expiredToken = Jwts.builder()
			.issuer(properties.issuer())
			.subject("25")
			.id("expired-id")
			.issuedAt(Date.from(now.minus(Duration.ofHours(2))))
			.expiration(Date.from(now.minus(Duration.ofHours(1))))
			.claim("tokenType", "REFRESH")
			.signWith(properties.signingKey(), Jwts.SIG.HS256)
			.compact();

		VerifiedRefreshToken identified = verifier.identify(expiredToken);

		assertThat(identified).isEqualTo(
			new VerifiedRefreshToken(25L, "expired-id")
		);
	}

	@Test
	void rejectsAccessTokenWhenIdentifyingForLogout() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(25L);
		when(member.getRole()).thenReturn(MemberRole.USER);
		String accessToken = new JwtTokenProvider(properties)
			.issue(member)
			.accessToken();

		assertThatThrownBy(() -> verifier.identify(accessToken))
			.isInstanceOfSatisfying(
				RefreshTokenVerificationException.class,
				exception -> assertThat(exception.getReason())
					.isEqualTo(RefreshTokenVerificationException.Reason.INVALID)
			);
	}

	@Test
	void rejectsTokenSignedWithDifferentKey() {
		JwtProperties otherProperties = new JwtProperties(
			"bidlive-test",
			"YW5vdGhlci10ZXN0LXNlY3JldC10aGF0LWlzLWxvbmdlci10aGFuLTMyLWJ5dGVz",
			Duration.ofMinutes(30),
			Duration.ofDays(14)
		);
		Instant now = Instant.now();
		String token = Jwts.builder()
			.issuer(properties.issuer())
			.subject("25")
			.id("other-key-id")
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(Duration.ofDays(1))))
			.claim("tokenType", "REFRESH")
			.signWith(otherProperties.signingKey(), Jwts.SIG.HS256)
			.compact();

		assertReason(token, RefreshTokenVerificationException.Reason.INVALID);
	}

	private void assertReason(
		String token,
		RefreshTokenVerificationException.Reason expected
	) {
		assertThatThrownBy(() -> verifier.verify(token))
			.isInstanceOfSatisfying(
				RefreshTokenVerificationException.class,
				exception -> assertThat(exception.getReason()).isEqualTo(expected)
			);
	}
}
