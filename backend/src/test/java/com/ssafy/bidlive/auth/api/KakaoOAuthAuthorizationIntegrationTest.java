package com.ssafy.bidlive.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class KakaoOAuthAuthorizationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void redirectsToKakaoAuthorizationEndpoint() throws Exception {
		MvcResult result = mockMvc.perform(get("/oauth2/authorization/kakao"))
			.andExpect(status().isFound())
			.andExpect(content().string(""))
			.andReturn();

		String location = result.getResponse().getRedirectedUrl();
		assertThat(location).isNotNull();

		var authorizationUri = UriComponentsBuilder.fromUriString(location).build();
		MultiValueMap<String, String> query = authorizationUri.getQueryParams();

		assertThat(authorizationUri.getScheme()).isEqualTo("https");
		assertThat(authorizationUri.getHost()).isEqualTo("kauth.kakao.com");
		assertThat(authorizationUri.getPath()).isEqualTo("/oauth/authorize");
		assertThat(query.getFirst("response_type")).isEqualTo("code");
		assertThat(query.getFirst("client_id")).isEqualTo("test-kakao-client-id");
		assertThat(query.getFirst("redirect_uri"))
			.isEqualTo("http://localhost/login/oauth2/code/kakao");
		assertThat(query.getFirst("state")).isNotBlank();
		assertThat(query).doesNotContainKeys("email", "password");
		assertThat(result.getRequest().getSession(false)).isNotNull();
	}

	@Test
	void returnsBadRequestForUnknownClientRegistration() throws Exception {
		mockMvc.perform(get("/oauth2/authorization/unknown"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith("application/json"))
			.andExpect(jsonPath("$.code").value("OAUTH_REQUEST_INVALID"));
	}
}
