package com.ssafy.bidlive.auth.application;

import static com.ssafy.bidlive.auth.domain.LogoutErrorCode.INVALID_LOGOUT_REQUEST;
import static com.ssafy.bidlive.auth.domain.LogoutErrorCode.LOGOUT_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 로그아웃 요청 검증과 Refresh Token 세션의 멱등한 폐기를 검증한다. */
@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

	private static final VerifiedRefreshToken VERIFIED_TOKEN =
		new VerifiedRefreshToken(25L, "refresh-id");

	@Mock
	private RefreshTokenVerifier refreshTokenVerifier;

	@Mock
	private RefreshTokenSessionStore refreshTokenSessionStore;

	private LogoutService logoutService;

	@BeforeEach
	void setUp() {
		logoutService = new LogoutService(
			refreshTokenVerifier,
			refreshTokenSessionStore
		);
	}

	@Test
	void deletesSessionIdentifiedFromRefreshToken() {
		when(refreshTokenVerifier.identify("refresh-token"))
			.thenReturn(VERIFIED_TOKEN);

		logoutService.logout("refresh-token");

		verify(refreshTokenSessionStore).delete(VERIFIED_TOKEN);
	}

	@Test
	void succeedsWhenExpiredOrAlreadyRevokedTokenHasNoStoredSession() {
		when(refreshTokenVerifier.identify("expired-refresh-token"))
			.thenReturn(VERIFIED_TOKEN);

		assertThatCode(() -> logoutService.logout("expired-refresh-token"))
			.doesNotThrowAnyException();
		verify(refreshTokenSessionStore).delete(VERIFIED_TOKEN);
	}

	@Test
	void rejectsMissingRefreshCookie() {
		assertLogoutError(null, INVALID_LOGOUT_REQUEST);
		assertLogoutError(" ", INVALID_LOGOUT_REQUEST);

		verifyNoInteractions(refreshTokenVerifier, refreshTokenSessionStore);
	}

	@Test
	void rejectsMalformedOrForgedRefreshToken() {
		when(refreshTokenVerifier.identify("invalid-refresh-token"))
			.thenThrow(new RefreshTokenVerificationException(
				RefreshTokenVerificationException.Reason.INVALID,
				new IllegalArgumentException("invalid token")
			));

		assertLogoutError("invalid-refresh-token", INVALID_LOGOUT_REQUEST);
		verifyNoInteractions(refreshTokenSessionStore);
	}

	@Test
	void reportsServerFailureWhenRedisSessionDeletionFails() {
		when(refreshTokenVerifier.identify("refresh-token"))
			.thenReturn(VERIFIED_TOKEN);
		doThrow(new IllegalStateException("redis unavailable"))
			.when(refreshTokenSessionStore)
			.delete(VERIFIED_TOKEN);

		assertLogoutError("refresh-token", LOGOUT_FAILED);
	}

	private void assertLogoutError(
		String refreshToken,
		com.ssafy.bidlive.auth.domain.LogoutErrorCode expected
	) {
		assertThatThrownBy(() -> logoutService.logout(refreshToken))
			.isInstanceOfSatisfying(
				LogoutException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(expected)
			);
	}
}
