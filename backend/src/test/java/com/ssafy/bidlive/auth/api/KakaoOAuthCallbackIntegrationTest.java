package com.ssafy.bidlive.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ssafy.bidlive.auth.application.OneTimeLoginCodeStore;
import com.ssafy.bidlive.auth.domain.SocialAccount;
import com.ssafy.bidlive.auth.domain.SocialProvider;
import com.ssafy.bidlive.auth.infrastructure.persistence.SocialAccountRepository;
import com.ssafy.bidlive.member.infrastructure.persistence.MemberRepository;
import com.ssafy.bidlive.wallet.infrastructure.persistence.WalletRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.UriComponentsBuilder;

/** 카카오 Callback의 상태 검증, 자동 가입과 프론트 Redirect 흐름을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class KakaoOAuthCallbackIntegrationTest {

	private static final HttpServer KAKAO_SERVER = startKakaoServer();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private SocialAccountRepository socialAccountRepository;

	@Autowired
	private WalletRepository walletRepository;

	@MockitoBean
	private OneTimeLoginCodeStore loginCodeStore;

	@DynamicPropertySource
	static void kakaoProviderProperties(DynamicPropertyRegistry registry) {
		registry.add(
			"spring.security.oauth2.client.provider.kakao.token-uri",
			() -> kakaoServerUri("/oauth/token")
		);
		registry.add(
			"spring.security.oauth2.client.provider.kakao.user-info-uri",
			() -> kakaoServerUri("/v2/user/me")
		);
	}

	@AfterAll
	static void stopKakaoServer() {
		KAKAO_SERVER.stop(0);
	}

	@Test
	void completesCallbackAndReusesMemberOnNextLogin() throws Exception {
		CallbackResult first = performSuccessfulCallback();
		CallbackResult second = performSuccessfulCallback();

		assertThat(first.loginCode()).isNotBlank().isNotEqualTo(second.loginCode());
		assertThat(memberRepository.count()).isEqualTo(1);
		assertThat(socialAccountRepository.count()).isEqualTo(1);
		assertThat(walletRepository.count()).isEqualTo(2);
		SocialAccount socialAccount = socialAccountRepository
			.findByProviderAndProviderUserId(SocialProvider.KAKAO, "987654321")
			.orElseThrow();
		assertThat(socialAccount.getEmail()).isEqualTo("member@example.com");
		assertThat(socialAccount.getProviderNickname()).isEqualTo("카카오 닉네임");
		assertThat(socialAccount.getProfileImageUrl())
			.isEqualTo("https://example.com/profile.png");
		verify(loginCodeStore, times(2)).save(
			anyString(),
			anyLong(),
			eq(Duration.ofMinutes(2))
		);
	}

	@Test
	void redirectsCallbackWithoutStoredStateAsInvalidState() throws Exception {
		mockMvc.perform(get("/login/oauth2/code/kakao")
				.queryParam("code", "authorization-code")
				.queryParam("state", "unknown-state"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl(
				"http://localhost:5173/oauth/callback?error=INVALID_OAUTH_STATE"
			));
	}

	@Test
	void redirectsUserDenialAsAccessDenied() throws Exception {
		MvcResult authorization = mockMvc.perform(get("/oauth2/authorization/kakao"))
			.andExpect(status().isFound())
			.andReturn();
		String state = queryParameter(
			authorization.getResponse().getRedirectedUrl(),
			"state"
		);

		mockMvc.perform(get("/login/oauth2/code/kakao")
				.session((org.springframework.mock.web.MockHttpSession) authorization
					.getRequest()
					.getSession(false))
				.queryParam("error", "access_denied")
				.queryParam("error_description", "user denied")
				.queryParam("state", state))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl(
				"http://localhost:5173/oauth/callback?error=OAUTH_ACCESS_DENIED"
			));
	}

	private CallbackResult performSuccessfulCallback() throws Exception {
		MvcResult authorization = mockMvc.perform(get("/oauth2/authorization/kakao"))
			.andExpect(status().isFound())
			.andReturn();
		String authorizationLocation = authorization.getResponse().getRedirectedUrl();
		String state = queryParameter(authorizationLocation, "state");

		MvcResult callback = mockMvc.perform(get("/login/oauth2/code/kakao")
				.session((org.springframework.mock.web.MockHttpSession) authorization
					.getRequest()
					.getSession(false))
				.queryParam("code", "single-use-authorization-code")
				.queryParam("state", state))
			.andExpect(status().isFound())
			.andReturn();

		String callbackLocation = callback.getResponse().getRedirectedUrl();
		assertThat(callbackLocation)
			.startsWith("http://localhost:5173/oauth/callback?code=")
			.doesNotContain(
				"single-use-authorization-code",
				"kakao-access-token",
				"refresh_token"
			);
		return new CallbackResult(queryParameter(callbackLocation, "code"));
	}

	private static String queryParameter(String uri, String name) {
		String value = UriComponentsBuilder.fromUriString(uri)
			.build()
			.getQueryParams()
			.getFirst(name);
		return value == null ? null : UriUtils.decode(value, StandardCharsets.UTF_8);
	}

	private static HttpServer startKakaoServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
			server.createContext("/oauth/token", exchange -> respondJson(exchange, """
				{"access_token":"kakao-access-token","token_type":"Bearer","expires_in":3600}
				"""));
			server.createContext("/v2/user/me", exchange -> respondJson(exchange, """
				{
				  "id":987654321,
				  "kakao_account":{
				    "email":"member@example.com",
				    "profile":{
				      "nickname":"카카오 닉네임",
				      "profile_image_url":"https://example.com/profile.png"
				    }
				  }
				}
				"""));
			server.start();
			return server;
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to start Kakao test server", exception);
		}
	}

	private static String kakaoServerUri(String path) {
		return URI.create("http://127.0.0.1:" + KAKAO_SERVER.getAddress().getPort() + path)
			.toString();
	}

	private static void respondJson(HttpExchange exchange, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
		exchange.sendResponseHeaders(200, bytes.length);
		try (OutputStream output = exchange.getResponseBody()) {
			output.write(bytes);
		}
	}

	private record CallbackResult(String loginCode) {
	}
}
