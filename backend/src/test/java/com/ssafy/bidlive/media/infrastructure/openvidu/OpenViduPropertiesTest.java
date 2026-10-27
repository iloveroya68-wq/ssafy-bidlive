package com.ssafy.bidlive.media.infrastructure.openvidu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/** OpenVidu 접속 설정의 필수값 검증과 Basic 인증 헤더 인코딩 규칙을 검증합니다. */
class OpenViduPropertiesTest {

	@Test
	void buildsBasicAuthorizationFromFixedApiUsernameAndSecret() {
		OpenViduProperties properties =
			new OpenViduProperties("https://i15a704.p.ssafy.io:4443", "openvidu-secret");

		String expected = "Basic " + Base64.getEncoder()
			.encodeToString("OPENVIDUAPP:openvidu-secret".getBytes(StandardCharsets.UTF_8));
		assertThat(properties.basicAuthorization()).isEqualTo(expected);
	}

	@Test
	void stripsTrailingSlashFromBaseUrl() {
		OpenViduProperties properties =
			new OpenViduProperties("https://i15a704.p.ssafy.io:4443/", "openvidu-secret");

		assertThat(properties.baseUrl()).isEqualTo("https://i15a704.p.ssafy.io:4443");
	}

	@Test
	void rejectsMissingConnectionSettings() {
		assertThatThrownBy(() -> new OpenViduProperties(" ", "openvidu-secret"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new OpenViduProperties("https://i15a704.p.ssafy.io:4443", null))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
