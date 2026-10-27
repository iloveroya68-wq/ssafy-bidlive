package com.ssafy.bidlive.auth.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.bidlive.auth.application.LogoutException;
import com.ssafy.bidlive.auth.application.LogoutService;
import com.ssafy.bidlive.auth.domain.LogoutErrorCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 로그아웃 API의 멱등 응답, CSRF 예외, 세션 쿠키 만료와 오류 코드를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class LogoutIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private LogoutService logoutService;

	@Test
	void revokesSessionWithoutAccessTokenAndExpiresSecureRefreshCookie()
		throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
				.cookie(new Cookie("refreshToken", "refresh-token")))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""))
			.andExpect(header().string(
				HttpHeaders.SET_COOKIE,
				containsString("refreshToken=")
			))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));

		verify(logoutService).logout("refresh-token");
	}

	@Test
	void succeedsEvenWhenOptionalAccessTokenIsExpired() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer expired-access-token")
				.cookie(new Cookie("refreshToken", "refresh-token")))
			.andExpect(status().isNoContent());
	}

	@Test
	void returnsInvalidRequestWhenRefreshCookieIsMissing() throws Exception {
		doThrow(new LogoutException(LogoutErrorCode.INVALID_LOGOUT_REQUEST))
			.when(logoutService)
			.logout(isNull());

		mockMvc.perform(post("/api/v1/auth/logout"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_LOGOUT_REQUEST"));
	}

	@Test
	void returnsServerErrorWhenSessionDeletionFails() throws Exception {
		doThrow(new LogoutException(LogoutErrorCode.LOGOUT_FAILED))
			.when(logoutService)
			.logout("refresh-token");

		mockMvc.perform(post("/api/v1/auth/logout")
				.cookie(new Cookie("refreshToken", "refresh-token")))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("LOGOUT_FAILED"));
	}
}
