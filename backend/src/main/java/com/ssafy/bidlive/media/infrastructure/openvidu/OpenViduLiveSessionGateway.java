package com.ssafy.bidlive.media.infrastructure.openvidu;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ssafy.bidlive.media.application.port.out.LiveSessionGateway;
import com.ssafy.bidlive.media.domain.LiveSessionRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** OpenVidu REST API를 직접 호출해 방송 세션을 확보하고 Connection 토큰을 발급받는 어댑터입니다. */
public class OpenViduLiveSessionGateway implements LiveSessionGateway {

	private static final String SESSIONS_PATH = "/openvidu/api/sessions";
	private static final String SESSION_PATH = "/openvidu/api/sessions/{sessionId}";
	private static final String CONNECTIONS_PATH = "/openvidu/api/sessions/{sessionId}/connection";
	private static final String WEBRTC_CONNECTION_TYPE = "WEBRTC";

	private static final Logger log = LoggerFactory.getLogger(OpenViduLiveSessionGateway.class);

	private final WebClient openViduWebClient;

	public OpenViduLiveSessionGateway(WebClient openViduWebClient) {
		this.openViduWebClient = openViduWebClient;
	}

	/**
	 * roomId를 customSessionId로 사용하므로 OpenVidu가 부여하는 세션 식별자도 roomId와 같아진다.
	 * 이미 같은 식별자의 세션이 있으면 OpenVidu는 409 CONFLICT를 반환하며, 이는 실패가 아니라
	 * 기존 세션을 그대로 재사용하면 되는 상태다.
	 */
	@Override
	public String ensureSession(String roomId) {
		try {
			SessionResponse response = openViduWebClient.post()
				.uri(SESSIONS_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("customSessionId", roomId))
				.retrieve()
				.bodyToMono(SessionResponse.class)
				.block();

			if (response == null || response.id() == null || response.id().isBlank()) {
				throw new OpenViduApiException(
					"OpenVidu did not return a session id for roomId=" + roomId);
			}
			return response.id();
		} catch (WebClientResponseException.Conflict alreadyExists) {
			return roomId;
		} catch (WebClientException exception) {
			throw new OpenViduApiException(
				"Failed to create OpenVidu session for roomId=" + roomId, exception);
		}
	}

	@Override
	public String issueToken(String sessionId, LiveSessionRole role) {
		try {
			ConnectionResponse response = openViduWebClient.post()
				.uri(CONNECTIONS_PATH, sessionId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of(
					"type", WEBRTC_CONNECTION_TYPE,
					"role", role.name()
				))
				.retrieve()
				.bodyToMono(ConnectionResponse.class)
				.block();

			if (response == null || response.token() == null || response.token().isBlank()) {
				throw new OpenViduApiException(
					"OpenVidu did not return a connection token for sessionId=" + sessionId);
			}
			return response.token();
		} catch (WebClientException exception) {
			throw new OpenViduApiException(
				"Failed to issue OpenVidu %s token for sessionId=%s".formatted(role, sessionId),
				exception);
		}
	}

	/**
	 * 세션을 지우면 OpenVidu가 참가자 연결까지 함께 닫는다. 이미 없는 세션은 404를 주는데,
	 * 종료라는 목표는 이미 달성된 상태이므로 실패로 올리지 않고 그대로 성공 처리한다.
	 */
	@Override
	public void closeSession(String sessionId) {
		try {
			openViduWebClient.delete()
				.uri(SESSION_PATH, sessionId)
				.retrieve()
				.bodyToMono(Void.class)
				.block();
		} catch (WebClientResponseException.NotFound alreadyClosed) {
			log.info("OpenVidu session {} was already closed", sessionId);
		} catch (WebClientException exception) {
			throw new OpenViduApiException(
				"Failed to close OpenVidu session for sessionId=" + sessionId, exception);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SessionResponse(String id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ConnectionResponse(String token) {
	}
}
