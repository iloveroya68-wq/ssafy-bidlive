package com.ssafy.bidlive.auth.application;

public interface RefreshTokenVerifier {

	VerifiedRefreshToken verify(String refreshToken);

	VerifiedRefreshToken identify(String refreshToken);
}
