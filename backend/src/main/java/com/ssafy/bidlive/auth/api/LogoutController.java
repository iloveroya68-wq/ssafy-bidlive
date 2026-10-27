package com.ssafy.bidlive.auth.api;

import java.time.Duration;

import com.ssafy.bidlive.auth.application.LogoutService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 현재 Refresh Token 세션을 폐기하고 브라우저 인증 쿠키를 만료시킨다. */
@RestController
@RequestMapping("/api/v1/auth")
public class LogoutController {

	private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
	private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

	private final LogoutService logoutService;

	public LogoutController(LogoutService logoutService) {
		this.logoutService = logoutService;
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
		@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false)
		String refreshToken
	) {
		logoutService.logout(refreshToken);

		ResponseCookie expiredRefreshCookie = ResponseCookie
			.from(REFRESH_TOKEN_COOKIE, "")
			.httpOnly(true)
			.secure(true)
			.sameSite("Lax")
			.path(REFRESH_COOKIE_PATH)
			.maxAge(Duration.ZERO)
			.build();

		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, expiredRefreshCookie.toString())
			.build();
	}
}
