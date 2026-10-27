package com.ssafy.bidlive.media.infrastructure.openvidu;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** OpenVidu 서버 접속 정보를 바인딩하고 REST 호출에 사용할 Basic 인증 헤더 값을 만듭니다. */
@ConfigurationProperties(prefix = "openvidu")
public record OpenViduProperties(
	String url,
	String secret
) {

	/** OpenVidu REST API의 Basic 인증 사용자명은 서버에 고정되어 있어 설정으로 노출하지 않는다. */
	private static final String API_USERNAME = "OPENVIDUAPP";

	public OpenViduProperties {
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("openvidu.url must not be blank");
		}
		if (secret == null || secret.isBlank()) {
			throw new IllegalArgumentException("openvidu.secret must not be blank");
		}
	}

	public String baseUrl() {
		String trimmed = url.strip();
		return trimmed.endsWith("/")
			? trimmed.substring(0, trimmed.length() - 1)
			: trimmed;
	}

	public String basicAuthorization() {
		String credentials = API_USERNAME + ":" + secret;
		String encoded = Base64.getEncoder()
			.encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
		return "Basic " + encoded;
	}
}
