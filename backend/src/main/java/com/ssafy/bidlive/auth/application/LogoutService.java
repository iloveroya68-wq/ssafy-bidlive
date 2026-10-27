package com.ssafy.bidlive.auth.application;

import static com.ssafy.bidlive.auth.domain.LogoutErrorCode.INVALID_LOGOUT_REQUEST;
import static com.ssafy.bidlive.auth.domain.LogoutErrorCode.LOGOUT_FAILED;

import org.springframework.stereotype.Service;

/** Refresh Token을 식별해 현재 로그인 세션을 멱등하게 폐기한다. */
@Service
public class LogoutService {

	private final RefreshTokenVerifier refreshTokenVerifier;
	private final RefreshTokenSessionStore refreshTokenSessionStore;

	public LogoutService(
		RefreshTokenVerifier refreshTokenVerifier,
		RefreshTokenSessionStore refreshTokenSessionStore
	) {
		this.refreshTokenVerifier = refreshTokenVerifier;
		this.refreshTokenSessionStore = refreshTokenSessionStore;
	}

	public void logout(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new LogoutException(INVALID_LOGOUT_REQUEST);
		}

		VerifiedRefreshToken verifiedToken;
		try {
			verifiedToken = refreshTokenVerifier.identify(refreshToken);
		} catch (RefreshTokenVerificationException exception) {
			throw new LogoutException(INVALID_LOGOUT_REQUEST, exception);
		}

		try {
			refreshTokenSessionStore.delete(verifiedToken);
		} catch (RuntimeException exception) {
			throw new LogoutException(LOGOUT_FAILED, exception);
		}
	}
}
