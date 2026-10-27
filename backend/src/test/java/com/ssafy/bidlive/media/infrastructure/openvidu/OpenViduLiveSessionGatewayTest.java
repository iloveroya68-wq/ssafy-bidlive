package com.ssafy.bidlive.media.infrastructure.openvidu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ssafy.bidlive.media.application.port.out.LiveSessionGatewayException;
import com.ssafy.bidlive.media.domain.LiveSessionRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** OpenVidu REST 요청 형식과 409 CONFLICT를 기존 세션 재사용으로 해석하는 정책을 검증합니다. */
class OpenViduLiveSessionGatewayTest {

	private static final String BASE_URL = "https://openvidu.test:4443";

	private final StubExchangeFunction exchangeFunction = new StubExchangeFunction();
	private final OpenViduLiveSessionGateway gateway = new OpenViduLiveSessionGateway(
		WebClient.builder()
			.baseUrl(BASE_URL)
			.exchangeFunction(exchangeFunction)
			.build()
	);

	@Test
	void createsSessionWithRoomIdAsCustomSessionId() {
		exchangeFunction.enqueueJson(HttpStatus.OK, """
			{"id":"room-123","createdAt":1750000000000}
			""");

		String sessionId = gateway.ensureSession("room-123");

		assertThat(sessionId).isEqualTo("room-123");

		RecordedRequest request = exchangeFunction.recorded().get(0);
		assertThat(request.method()).isEqualTo(HttpMethod.POST);
		assertThat(request.url()).isEqualTo(BASE_URL + "/openvidu/api/sessions");
		assertThat(request.body()).isEqualTo("{\"customSessionId\":\"room-123\"}");
	}

	@Test
	void reusesExistingSessionWhenOpenViduReportsConflict() {
		exchangeFunction.enqueue(ClientResponse.create(HttpStatus.CONFLICT).build());

		assertThat(gateway.ensureSession("room-123")).isEqualTo("room-123");
	}

	@Test
	void issuesConnectionTokenForRequestedRole() {
		exchangeFunction.enqueueJson(HttpStatus.OK, """
			{"id":"con_abc","token":"wss://openvidu.test?sessionId=room-123&token=tok_sub","role":"SUBSCRIBER"}
			""");

		String token = gateway.issueToken("room-123", LiveSessionRole.SUBSCRIBER);

		assertThat(token).isEqualTo("wss://openvidu.test?sessionId=room-123&token=tok_sub");

		RecordedRequest request = exchangeFunction.recorded().get(0);
		assertThat(request.url())
			.isEqualTo(BASE_URL + "/openvidu/api/sessions/room-123/connection");
		assertThat(request.body()).contains("\"role\":\"SUBSCRIBER\"", "\"type\":\"WEBRTC\"");
	}

	@Test
	void wrapsOpenViduFailureIntoDomainException() {
		exchangeFunction.enqueue(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());

		assertThatThrownBy(() -> gateway.issueToken("room-123", LiveSessionRole.PUBLISHER))
			.isInstanceOf(OpenViduApiException.class)
			.hasMessageContaining("room-123");
	}

	@Test
	void failsWhenOpenViduOmitsToken() {
		exchangeFunction.enqueueJson(HttpStatus.OK, "{\"id\":\"con_abc\"}");

		assertThatThrownBy(() -> gateway.issueToken("room-123", LiveSessionRole.PUBLISHER))
			.isInstanceOf(OpenViduApiException.class);
	}

	@Test
	void closesSessionByDeletingItOnOpenVidu() {
		exchangeFunction.enqueue(ClientResponse.create(HttpStatus.NO_CONTENT).build());

		gateway.closeSession("room-123");

		RecordedRequest request = exchangeFunction.recorded().get(0);
		assertThat(request.method()).isEqualTo(HttpMethod.DELETE);
		assertThat(request.url()).isEqualTo(BASE_URL + "/openvidu/api/sessions/room-123");
	}

	/** 이미 사라진 세션은 종료 목표가 달성된 상태이므로 실패로 올리지 않는다. */
	@Test
	void treatsMissingSessionAsAlreadyClosed() {
		exchangeFunction.enqueue(ClientResponse.create(HttpStatus.NOT_FOUND).build());

		gateway.closeSession("room-123");
	}

	@Test
	void wrapsCloseFailureIntoDomainException() {
		exchangeFunction.enqueue(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());

		assertThatThrownBy(() -> gateway.closeSession("room-123"))
			.isInstanceOf(OpenViduApiException.class)
			.isInstanceOf(LiveSessionGatewayException.class)
			.hasMessageContaining("room-123");
	}

	private record RecordedRequest(HttpMethod method, String url, String body) {
	}

	/** 실제 OpenVidu 서버 없이 응답을 미리 지정하고 전송된 요청을 그대로 기록한다. */
	private static final class StubExchangeFunction implements ExchangeFunction {

		private final Deque<ClientResponse> responses = new ArrayDeque<>();
		private final List<RecordedRequest> recorded = new ArrayList<>();

		@Override
		public Mono<ClientResponse> exchange(ClientRequest request) {
			recorded.add(new RecordedRequest(
				request.method(),
				request.url().toString(),
				readBody(request)
			));
			ClientResponse response = responses.poll();
			if (response == null) {
				throw new IllegalStateException("No stubbed OpenVidu response left");
			}
			return Mono.just(response);
		}

		private void enqueue(ClientResponse response) {
			responses.add(response);
		}

		private void enqueueJson(HttpStatus status, String body) {
			enqueue(ClientResponse.create(status)
				.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				.body(body)
				.build());
		}

		private List<RecordedRequest> recorded() {
			return recorded;
		}

		private static String readBody(ClientRequest request) {
			MockClientHttpRequest mockRequest =
				new MockClientHttpRequest(request.method(), request.url());
			request.body().insert(mockRequest, new BodyInserter.Context() {

				@Override
				public List<HttpMessageWriter<?>> messageWriters() {
					return ExchangeStrategies.withDefaults().messageWriters();
				}

				@Override
				public Optional<ServerHttpRequest> serverRequest() {
					return Optional.empty();
				}

				@Override
				public Map<String, Object> hints() {
					return Map.of();
				}
			}).block();
			return mockRequest.getBodyAsString().block();
		}
	}
}
