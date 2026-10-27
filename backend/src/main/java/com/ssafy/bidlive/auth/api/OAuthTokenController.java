package com.ssafy.bidlive.auth.api;

import com.ssafy.bidlive.auth.api.dto.OAuthTokenExchangeRequest;
import com.ssafy.bidlive.auth.api.dto.OAuthTokenExchangeResponse;
import com.ssafy.bidlive.auth.application.OAuthTokenExchangeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/oauth2")
public class OAuthTokenController {

	private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
	private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

	private final OAuthTokenExchangeService tokenExchangeService;

	public OAuthTokenController(OAuthTokenExchangeService tokenExchangeService) {
		this.tokenExchangeService = tokenExchangeService;
	}

	@PostMapping("/token")
	public ResponseEntity<OAuthTokenExchangeResponse> exchange(
		@Valid @RequestBody OAuthTokenExchangeRequest request
	) {
		OAuthTokenExchangeService.OAuthTokenExchangeResult result =
			tokenExchangeService.exchange(request.loginCode());

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
			.body(OAuthTokenExchangeResponse.from(result));
	}
}
