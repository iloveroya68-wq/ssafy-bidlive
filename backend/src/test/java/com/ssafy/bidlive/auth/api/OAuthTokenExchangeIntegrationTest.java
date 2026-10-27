package com.ssafy.bidlive.auth.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.ssafy.bidlive.auth.application.OAuthTokenExchangeException;
import com.ssafy.bidlive.auth.application.OAuthTokenExchangeService;
import com.ssafy.bidlive.auth.domain.OAuthTokenExchangeErrorCode;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OAuthTokenExchangeIntegrationTest {

	private static final String LOGIN_CODE = "a".repeat(43);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OAuthTokenExchangeService tokenExchangeService;

	@Test
	void exchangesLoginCodeAndReturnsMemberStatusRoleAndSecureCookie() throws Exception {
		when(tokenExchangeService.exchange(LOGIN_CODE)).thenReturn(successResult());

		mockMvc.perform(post("/api/v1/auth/oauth2/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"loginCode":"%s"}
					""".formatted(LOGIN_CODE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.accessToken").value("access-token"))
			.andExpect(jsonPath("$.expiresIn").value(1800))
			.andExpect(jsonPath("$.member.memberId").value(25))
			.andExpect(jsonPath("$.member.nickname").doesNotExist())
			.andExpect(jsonPath("$.member.onboardingRequired").value(true))
			.andExpect(jsonPath("$.member.status").value("SUSPENDED"))
			.andExpect(jsonPath("$.member.role").value("USER"))
			.andExpect(header().string(
				HttpHeaders.SET_COOKIE,
				containsString("refreshToken=refresh-token")
			))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
	}

	@Test
	void returnsSpecifiedErrorResponse() throws Exception {
		when(tokenExchangeService.exchange(anyString())).thenThrow(
			new OAuthTokenExchangeException(
				OAuthTokenExchangeErrorCode.LOGIN_CODE_ALREADY_USED
			)
		);

		mockMvc.perform(post("/api/v1/auth/oauth2/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"loginCode":"%s"}
					""".formatted(LOGIN_CODE)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("LOGIN_CODE_ALREADY_USED"));
	}

	@Test
	void returnsInvalidLoginCodeForBlankRequest() throws Exception {
		mockMvc.perform(post("/api/v1/auth/oauth2/token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"loginCode":""}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_LOGIN_CODE"));
	}

	@Test
	void allowsCredentialedCorsFromConfiguredFrontend() throws Exception {
		mockMvc.perform(options("/api/v1/auth/oauth2/token")
				.header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(
					HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
					HttpHeaders.CONTENT_TYPE
				))
			.andExpect(status().isOk())
			.andExpect(header().string(
				HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
				"http://localhost:5173"
			))
			.andExpect(header().string(
				HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
				"true"
			));
	}

	private OAuthTokenExchangeService.OAuthTokenExchangeResult successResult() {
		return new OAuthTokenExchangeService.OAuthTokenExchangeResult(
			"access-token",
			"refresh-token",
			Duration.ofMinutes(30),
			Duration.ofDays(14),
			new OAuthTokenExchangeService.MemberSummary(
				25L,
				null,
				true,
				MemberStatus.SUSPENDED,
				MemberRole.USER
			)
		);
	}
}
