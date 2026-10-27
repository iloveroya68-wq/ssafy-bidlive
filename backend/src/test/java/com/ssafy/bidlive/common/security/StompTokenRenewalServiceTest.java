package com.ssafy.bidlive.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** STOMP 토큰 재인증의 회원 일치·상태 판정과 인증 실패 매핑을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class StompTokenRenewalServiceTest {

	private static final String TOKEN = "sensitive-renewal-access-token";
	private static final long MEMBER_ID = 25L;
	private static final long OTHER_MEMBER_ID = 88L;
	private static final Instant EXPIRES_AT = Instant.parse("2026-07-29T10:00:00Z");

	@Mock
	private AccessTokenVerifier accessTokenVerifier;

	@Mock
	private MemberPersistence memberPersistence;

	private StompTokenRenewalService service;
	private SecurityPrincipal currentPrincipal;

	@BeforeEach
	void setUp() {
		service = new StompTokenRenewalService(
			accessTokenVerifier,
			memberPersistence
		);
		currentPrincipal = new SecurityPrincipal(MEMBER_ID, MemberRole.ADMIN);
	}

	@Test
	void renewsActiveMemberWithVerifiedExpirationOnly() {
		givenVerifiedToken(MEMBER_ID, MemberRole.USER);
		givenMember(MEMBER_ID, MemberStatus.ACTIVE);

		StompTokenRenewalResult result = service.renew(currentPrincipal, TOKEN);

		assertThat(result.tokenExpiresAt()).isEqualTo(EXPIRES_AT);
		assertThat(currentPrincipal.role()).isEqualTo(MemberRole.ADMIN);
		verify(memberPersistence).findById(MEMBER_ID);
	}

	@Test
	void renewsSuspendedMemberWithoutReplacingPrincipalRole() {
		givenVerifiedToken(MEMBER_ID, MemberRole.USER);
		givenMember(MEMBER_ID, MemberStatus.SUSPENDED);

		StompTokenRenewalResult result = service.renew(currentPrincipal, TOKEN);

		assertThat(result.tokenExpiresAt()).isEqualTo(EXPIRES_AT);
		assertThat(currentPrincipal.role()).isEqualTo(MemberRole.ADMIN);
	}

	@Test
	void rejectsTokenForDifferentMemberBeforeMemberLookup() {
		givenVerifiedToken(OTHER_MEMBER_ID, MemberRole.USER);

		assertRenewalFailure(
			StompTokenRenewalErrorCode.AUTH_PRINCIPAL_MISMATCH,
			TOKEN,
			Long.toString(MEMBER_ID),
			Long.toString(OTHER_MEMBER_ID)
		);
		verifyNoInteractions(memberPersistence);
	}

	@Test
	void rejectsExpiredTokenWithoutMemberLookup() {
		when(accessTokenVerifier.verify(TOKEN)).thenThrow(
			new AccessTokenVerificationException(AccessTokenFailureReason.EXPIRED)
		);

		assertRenewalFailure(
			StompTokenRenewalErrorCode.AUTH_RENEW_EXPIRED,
			TOKEN
		);
		verifyNoInteractions(memberPersistence);
	}

	@ParameterizedTest
	@EnumSource(
		value = AccessTokenFailureReason.class,
		names = "EXPIRED",
		mode = EnumSource.Mode.EXCLUDE
	)
	void mapsEveryNonExpiredTokenFailureToInvalid(
		AccessTokenFailureReason reason
	) {
		when(accessTokenVerifier.verify(TOKEN)).thenThrow(
			new AccessTokenVerificationException(reason)
		);

		assertRenewalFailure(
			StompTokenRenewalErrorCode.AUTH_RENEW_INVALID,
			TOKEN
		);
		verifyNoInteractions(memberPersistence);
	}

	@Test
	void rejectsMissingMemberAsInvalid() {
		givenVerifiedToken(MEMBER_ID, MemberRole.USER);
		when(memberPersistence.findById(MEMBER_ID)).thenReturn(Optional.empty());

		assertRenewalFailure(
			StompTokenRenewalErrorCode.AUTH_RENEW_INVALID,
			TOKEN,
			Long.toString(MEMBER_ID)
		);
	}

	@Test
	void rejectsWithdrawnMemberAsInvalidWithoutSensitiveDetails() {
		givenVerifiedToken(MEMBER_ID, MemberRole.USER);
		givenMember(MEMBER_ID, MemberStatus.WITHDRAWN);

		assertRenewalFailure(
			StompTokenRenewalErrorCode.AUTH_RENEW_INVALID,
			TOKEN,
			Long.toString(MEMBER_ID),
			MemberStatus.WITHDRAWN.name()
		);
	}

	@Test
	void rejectsMissingCurrentPrincipalWithoutTokenVerification() {
		assertRenewalFailure(
			null,
			StompTokenRenewalErrorCode.AUTH_RENEW_INVALID,
			TOKEN
		);
		verifyNoInteractions(accessTokenVerifier, memberPersistence);
	}

	@Test
	void rejectsMissingResultExpiration() {
		assertThatThrownBy(() -> new StompTokenRenewalResult(null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("tokenExpiresAt");
	}

	private void givenVerifiedToken(Long memberId, MemberRole tokenRole) {
		when(accessTokenVerifier.verify(TOKEN)).thenReturn(
			new VerifiedAccessToken(memberId, tokenRole, EXPIRES_AT)
		);
	}

	private void givenMember(Long memberId, MemberStatus status) {
		Member member = mock(Member.class);
		when(member.getStatus()).thenReturn(status);
		when(memberPersistence.findById(memberId)).thenReturn(Optional.of(member));
	}

	private void assertRenewalFailure(
		StompTokenRenewalErrorCode expectedCode,
		String... hiddenValues
	) {
		assertRenewalFailure(currentPrincipal, expectedCode, hiddenValues);
	}

	private void assertRenewalFailure(
		SecurityPrincipal principal,
		StompTokenRenewalErrorCode expectedCode,
		String... hiddenValues
	) {
		assertThatThrownBy(() -> service.renew(principal, TOKEN))
			.isInstanceOfSatisfying(
				StompTokenRenewalException.class,
				exception -> {
					assertThat(exception.errorCode()).isEqualTo(expectedCode);
					assertThat(exception.getMessage()).doesNotContain(hiddenValues);
				}
			);
	}
}
