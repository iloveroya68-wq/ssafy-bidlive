package com.ssafy.bidlive.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.function.Consumer;

import javax.crypto.SecretKey;

import com.ssafy.bidlive.member.domain.MemberRole;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** JWT Access Token의 정상 검증과 실패 사유 분류를 검증합니다. */
class JwtAccessTokenVerifierTest {

	private static final String SECRET =
		"dGVzdC1qd3Qtc2VjcmV0LW11c3QtYmUtYXQtbGVhc3QtMzItYnl0ZXMtbG9uZw==";
	private static final String OTHER_SECRET =
		"YW5vdGhlci10ZXN0LWp3dC1zZWNyZXQtbXVzdC1iZS1hdC1sZWFzdC0zMi1ieXRlcw==";
	private static final String HS384_SECRET =
		"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVm";
	private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");
	private static final Instant EXPIRES_AT = NOW.plus(Duration.ofMinutes(30));

	private JwtProperties properties;
	private JwtAccessTokenVerifier verifier;

	@BeforeEach
	void setUp() {
		properties = new JwtProperties(
			"bidlive-test",
			SECRET,
			Duration.ofMinutes(30),
			Duration.ofDays(14)
		);
		verifier = new JwtAccessTokenVerifier(
			properties,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void verifiesValidAccessToken() {
		String token = accessToken(builder -> {
		});

		VerifiedAccessToken verified = verifier.verify(token);

		assertThat(verified.memberId()).isEqualTo(25L);
		assertThat(verified.role()).isEqualTo(MemberRole.ADMIN);
		assertThat(verified.expiresAt()).isEqualTo(EXPIRES_AT);
	}

	@Test
	void rejectsMissingToken() {
		assertFailure(null, AccessTokenFailureReason.MISSING);
		assertFailure("   ", AccessTokenFailureReason.MISSING);
	}

	@Test
	void rejectsExpiredToken() {
		String token = token(builder -> builder
			.expiration(Date.from(NOW.minusSeconds(1))));

		assertFailure(token, AccessTokenFailureReason.EXPIRED);
	}

	@Test
	void rejectsInvalidSignature() {
		String token = accessToken(builder -> {
		}, signingKey(OTHER_SECRET));

		assertFailure(token, AccessTokenFailureReason.INVALID_SIGNATURE);
	}

	@Test
	void rejectsUnexpectedSignatureAlgorithm() {
		JwtProperties hs384Properties = new JwtProperties(
			"bidlive-test",
			HS384_SECRET,
			Duration.ofMinutes(30),
			Duration.ofDays(14)
		);
		JwtAccessTokenVerifier hs384Verifier = new JwtAccessTokenVerifier(
			hs384Properties,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
		String token = Jwts.builder()
			.issuer(hs384Properties.issuer())
			.subject("25")
			.expiration(Date.from(EXPIRES_AT))
			.claim("tokenType", "ACCESS")
			.claim("role", "ADMIN")
			.signWith(hs384Properties.signingKey(), Jwts.SIG.HS384)
			.compact();

		assertThatThrownBy(() -> hs384Verifier.verify(token))
			.isInstanceOfSatisfying(
				AccessTokenVerificationException.class,
				exception -> assertThat(exception.reason())
					.isEqualTo(AccessTokenFailureReason.INVALID_SIGNATURE)
			);
	}

	@Test
	void rejectsUnexpectedIssuer() {
		String token = accessToken(builder -> builder.issuer("other-service"));

		assertFailure(token, AccessTokenFailureReason.INVALID_ISSUER);
	}

	@Test
	void rejectsRefreshToken() {
		String token = accessToken(builder -> builder
			.claim("tokenType", "REFRESH"));

		assertFailure(token, AccessTokenFailureReason.INVALID_TOKEN_TYPE);
	}

	@Test
	void rejectsMissingSubject() {
		String token = accessToken(builder -> builder.subject(null));

		assertFailure(token, AccessTokenFailureReason.INVALID_CLAIMS);
	}

	@Test
	void rejectsNonNumericSubject() {
		String token = accessToken(builder -> builder.subject("member-25"));

		assertFailure(token, AccessTokenFailureReason.INVALID_CLAIMS);
	}

	@Test
	void rejectsNonPositiveMemberId() {
		String token = accessToken(builder -> builder.subject("0"));

		assertFailure(token, AccessTokenFailureReason.INVALID_CLAIMS);
	}

	@Test
	void rejectsMissingRole() {
		String token = token(builder -> {
		});

		assertFailure(token, AccessTokenFailureReason.INVALID_CLAIMS);
	}

	@Test
	void rejectsUnknownRole() {
		String token = accessToken(builder -> builder.claim("role", "SUPER_ADMIN"));

		assertFailure(token, AccessTokenFailureReason.INVALID_CLAIMS);
	}

	@Test
	void rejectsMissingExpiration() {
		String token = Jwts.builder()
			.issuer(properties.issuer())
			.subject("25")
			.claim("tokenType", "ACCESS")
			.claim("role", "ADMIN")
			.signWith(properties.signingKey(), Jwts.SIG.HS256)
			.compact();

		assertFailure(token, AccessTokenFailureReason.INVALID_CLAIMS);
	}

	@Test
	void rejectsMalformedTokenWithoutExposingIt() {
		String token = "not-a-jwt-token";

		assertThatThrownBy(() -> verifier.verify(token))
			.isInstanceOfSatisfying(
				AccessTokenVerificationException.class,
				exception -> {
					assertThat(exception.reason()).isEqualTo(AccessTokenFailureReason.MALFORMED);
					assertThat(exception.getMessage()).doesNotContain(token);
				}
			);
	}

	private String accessToken(Consumer<JwtBuilder> customizer) {
		return accessToken(customizer, properties.signingKey());
	}

	private String accessToken(
		Consumer<JwtBuilder> customizer,
		SecretKey signingKey
	) {
		return token(builder -> {
			builder.claim("role", "ADMIN");
			customizer.accept(builder);
		}, signingKey);
	}

	private String token(Consumer<JwtBuilder> customizer) {
		return token(customizer, properties.signingKey());
	}

	private String token(
		Consumer<JwtBuilder> customizer,
		SecretKey signingKey
	) {
		JwtBuilder builder = Jwts.builder()
			.issuer(properties.issuer())
			.subject("25")
			.expiration(Date.from(EXPIRES_AT))
			.claim("tokenType", "ACCESS");
		customizer.accept(builder);
		return builder
			.signWith(signingKey, Jwts.SIG.HS256)
			.compact();
	}

	private SecretKey signingKey(String secretBase64) {
		return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
	}

	private void assertFailure(
		String token,
		AccessTokenFailureReason expectedReason
	) {
		assertThatThrownBy(() -> verifier.verify(token))
			.isInstanceOfSatisfying(
				AccessTokenVerificationException.class,
				exception -> {
					assertThat(exception.reason()).isEqualTo(expectedReason);
					if (token != null && !token.isBlank()) {
						assertThat(exception.getMessage()).doesNotContain(token);
					}
				}
			);
	}
}
