package com.ssafy.bidlive.auth.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.ssafy.bidlive.auth.application.AccessTokenRefreshException;
import com.ssafy.bidlive.auth.application.AccessTokenRefreshService;
import com.ssafy.bidlive.auth.domain.AccessTokenRefreshErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Refresh Token 쿠키 기반 재발급 API의 응답, 오류 코드와 보안 쿠키를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class AccessTokenRefreshIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AccessTokenRefreshService accessTokenRefreshService;

	@Test
	void returnsNewAccessTokenAndRotatedSecureRefreshCookieWithoutRequestBody()
		throws Exception {
		when(accessTokenRefreshService.refresh("current-refresh-token"))
			.thenReturn(successResult());

		mockMvc.perform(post("/api/v1/auth/refresh")
				.cookie(new Cookie("refreshToken", "current-refresh-token")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.accessToken").value("replacement-access-token"))
			.andExpect(jsonPath("$.expiresIn").value(1800))
			.andExpect(header().string(
				HttpHeaders.SET_COOKIE,
				containsString("refreshToken=replacement-refresh-token")
			))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
	}

	@Test
	void returnsMissingErrorWhenRefreshCookieIsAbsent() throws Exception {
		when(accessTokenRefreshService.refresh(isNull())).thenThrow(
			new AccessTokenRefreshException(
				AccessTokenRefreshErrorCode.REFRESH_TOKEN_MISSING
			)
		);

		mockMvc.perform(post("/api/v1/auth/refresh"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_MISSING"));
	}

	@Test
	void returnsExpiredErrorForExpiredRefreshToken() throws Exception {
		when(accessTokenRefreshService.refresh("expired-refresh-token")).thenThrow(
			new AccessTokenRefreshException(
				AccessTokenRefreshErrorCode.REFRESH_TOKEN_EXPIRED
			)
		);

		mockMvc.perform(post("/api/v1/auth/refresh")
				.cookie(new Cookie("refreshToken", "expired-refresh-token")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_EXPIRED"));
	}

	private AccessTokenRefreshService.AccessTokenRefreshResult successResult() {
		return new AccessTokenRefreshService.AccessTokenRefreshResult(
			"replacement-access-token",
			"replacement-refresh-token",
			Duration.ofMinutes(30),
			Duration.ofDays(14)
		);
	}
}
