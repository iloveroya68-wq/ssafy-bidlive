package com.ssafy.bidlive.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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

/** STOMP CONNECT의 회원 상태 판정과 인증 오류 축약 정책을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class StompConnectAuthenticationServiceTest {

	private static final String TOKEN = "sensitive-access-token";
	private static final long MEMBER_ID = 25L;
	private static final Instant EXPIRES_AT = Instant.parse("2026-07-28T01:00:00Z");

	@Mock
	private AccessTokenVerifier accessTokenVerifier;

	@Mock
	private MemberPersistence memberPersistence;

	private StompConnectAuthenticationService service;

	@BeforeEach
	void setUp() {
		service = new StompConnectAuthenticationService(accessTokenVerifier, memberPersistence);
	}

	@Test
	void authenticatesActiveMemberUsingCurrentDatabaseRole() {
		givenVerifiedToken(MemberRole.ADMIN);
		givenMember(MemberStatus.ACTIVE, MemberRole.USER);

		StompAuthenticationResult result = service.authenticate(TOKEN);

		assertThat(result.principal().memberId()).isEqualTo(MEMBER_ID);
		assertThat(result.principal().role()).isEqualTo(MemberRole.USER);
		assertThat(result.tokenExpiresAt()).isEqualTo(EXPIRES_AT);
	}

	@Test
	void authenticatesSuspendedMemberUsingCurrentDatabaseRole() {
		givenVerifiedToken(MemberRole.USER);
		givenMember(MemberStatus.SUSPENDED, MemberRole.ADMIN);

		StompAuthenticationResult result = service.authenticate(TOKEN);

		assertThat(result.principal().role()).isEqualTo(MemberRole.ADMIN);
	}

	@Test
	void rejectsWithdrawnMemberWithoutExposingMemberDetails() {
		givenVerifiedToken(MemberRole.USER);
		Member member = mock(Member.class);
		when(member.getStatus()).thenReturn(MemberStatus.WITHDRAWN);
		when(memberPersistence.findById(MEMBER_ID)).thenReturn(Optional.of(member));

		assertAuthenticationFailure(
			StompAuthenticationErrorCode.AUTH_INVALID,
			MEMBER_ID + "",
			MemberStatus.WITHDRAWN.name(),
			TOKEN
		);
	}

	@Test
	void rejectsMissingMemberAsInvalidAuthentication() {
		givenVerifiedToken(MemberRole.USER);
		when(memberPersistence.findById(MEMBER_ID)).thenReturn(Optional.empty());

		assertAuthenticationFailure(StompAuthenticationErrorCode.AUTH_INVALID, MEMBER_ID + "", TOKEN);
	}

	@Test
	void preservesExpiredTokenAsExpiredAuthentication() {
		when(accessTokenVerifier.verify(TOKEN)).thenThrow(
			new AccessTokenVerificationException(AccessTokenFailureReason.EXPIRED)
		);

		assertAuthenticationFailure(StompAuthenticationErrorCode.AUTH_EXPIRED, TOKEN);
		verifyNoInteractions(memberPersistence);
	}

	@ParameterizedTest
	@EnumSource(
		value = AccessTokenFailureReason.class,
		names = "EXPIRED",
		mode = EnumSource.Mode.EXCLUDE
	)
	void mapsEveryNonExpiredTokenFailureToInvalidAuthentication(AccessTokenFailureReason reason) {
		when(accessTokenVerifier.verify(TOKEN)).thenThrow(
			new AccessTokenVerificationException(reason)
		);

		assertAuthenticationFailure(StompAuthenticationErrorCode.AUTH_INVALID, TOKEN);
		verifyNoInteractions(memberPersistence);
	}

	@Test
	void rejectsMemberWithoutCurrentRole() {
		givenVerifiedToken(MemberRole.ADMIN);
		Member member = mock(Member.class);
		when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
		when(member.getRole()).thenReturn(null);
		when(memberPersistence.findById(MEMBER_ID)).thenReturn(Optional.of(member));

		assertAuthenticationFailure(StompAuthenticationErrorCode.AUTH_INVALID, MEMBER_ID + "", TOKEN);
	}

	private void givenVerifiedToken(MemberRole tokenRole) {
		when(accessTokenVerifier.verify(TOKEN)).thenReturn(
			new VerifiedAccessToken(MEMBER_ID, tokenRole, EXPIRES_AT)
		);
	}

	private void givenMember(MemberStatus status, MemberRole currentRole) {
		Member member = mock(Member.class);
		when(member.getStatus()).thenReturn(status);
		when(member.getRole()).thenReturn(currentRole);
		when(memberPersistence.findById(MEMBER_ID)).thenReturn(Optional.of(member));
	}

	private void assertAuthenticationFailure(
		StompAuthenticationErrorCode expectedCode,
		String... hiddenValues
	) {
		assertThatThrownBy(() -> service.authenticate(TOKEN))
			.isInstanceOfSatisfying(
				StompAuthenticationException.class,
				exception -> {
					assertThat(exception.errorCode()).isEqualTo(expectedCode);
					assertThat(exception.getMessage()).doesNotContain(hiddenValues);
				}
			);
	}
}
