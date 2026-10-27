package com.ssafy.bidlive.auth.api;

import com.ssafy.bidlive.auth.api.dto.AccessTokenRefreshResponse;
import com.ssafy.bidlive.auth.application.AccessTokenRefreshService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HttpOnly Refresh Token 쿠키를 받아 새 Access Token과 회전된 쿠키를 반환한다. */
@RestController
@RequestMapping("/api/v1/auth")
public class AccessTokenRefreshController {

	private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
	private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

	private final AccessTokenRefreshService accessTokenRefreshService;

	public AccessTokenRefreshController(
		AccessTokenRefreshService accessTokenRefreshService
	) {
		this.accessTokenRefreshService = accessTokenRefreshService;
	}

	@PostMapping("/refresh")
	public ResponseEntity<AccessTokenRefreshResponse> refresh(
		@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false)
		String refreshToken
	) {
		AccessTokenRefreshService.AccessTokenRefreshResult result =
			accessTokenRefreshService.refresh(refreshToken);

		ResponseCookie refreshCookie = ResponseCookie
			.from(REFRESH_TOKEN_COOKIE, result.refreshToken())
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.path(REFRESH_COOKIE_PATH)
			.maxAge(result.refreshTokenTtl())
			.build();

		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
			.body(AccessTokenRefreshResponse.from(result));
	}
}
