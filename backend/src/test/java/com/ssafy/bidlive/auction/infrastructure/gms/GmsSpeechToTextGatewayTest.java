package com.ssafy.bidlive.auction.infrastructure.gms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.auction.infrastructure.CatchUpSummaryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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

/** SSAFY GMS Whisper의 URL, 인증과 multipart 요청 계약을 외부 호출 없이 검증한다. */
class GmsSpeechToTextGatewayTest {

	private static final String BASE_URL =
		"https://gms.ssafy.io/gmsapi/api.openai.com/v1";

	@Test
	void sendsOpenAiCompatibleWhisperRequest() {
		RecordingExchangeFunction exchange = new RecordingExchangeFunction();
		WebClient webClient = WebClient.builder()
			.baseUrl(BASE_URL)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-gms-key")
			.exchangeFunction(exchange)
			.build();
		CatchUpSummaryProperties properties = new CatchUpSummaryProperties();
		properties.setSttEnabled(true);
		properties.setTranscriptionPath("/audio/transcriptions");
		properties.setTranscriptionModel("whisper-1");
		GmsSpeechToTextGateway gateway = new GmsSpeechToTextGateway(
			webClient,
			new ObjectMapper(),
			properties
		);

		String transcript = gateway.transcribe("audio/webm", new byte[] {1, 2, 3});

		assertThat(transcript).isEqualTo("상품은 정상 작동합니다.");
		assertThat(exchange.method).isEqualTo(HttpMethod.POST);
		assertThat(exchange.url).isEqualTo(BASE_URL + "/audio/transcriptions");
		assertThat(exchange.authorization).isEqualTo("Bearer test-gms-key");
		assertThat(exchange.contentType).startsWith("multipart/form-data");
		assertThat(exchange.contentLength).isEqualTo(exchange.bodyBytes.length);
		assertThat(exchange.body).contains(
			"name=\"file\"",
			"filename=\"chunk.webm\"",
			"Content-Type: audio/webm",
			"name=\"model\"",
			"whisper-1"
		);
		assertThat(exchange.body.indexOf("name=\"file\"")).isLessThan(
			exchange.body.indexOf("name=\"model\"")
		);
		assertThat(exchange.body).doesNotContain("name=\"language\"")
			.doesNotContain("name=\"response_format\"");
	}

	@Test
	void exposesOnlyStructuredGmsErrorDetails() {
		RecordingExchangeFunction exchange = new RecordingExchangeFunction();
		exchange.respond(
			HttpStatus.BAD_REQUEST,
			"{\"error\":{\"code\":\"invalid_audio_format\","
				+ "\"message\":\"Audio file is not supported\"}}"
		);
		GmsSpeechToTextGateway gateway = gateway(exchange);

		assertThatThrownBy(() -> gateway.transcribe("audio/webm", new byte[] {1, 2, 3}))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("status=400")
			.hasMessageContaining("code=invalid_audio_format")
			.hasMessageContaining("message=Audio file is not supported")
			.hasMessageNotContaining("test-gms-key");
	}

	private GmsSpeechToTextGateway gateway(RecordingExchangeFunction exchange) {
		WebClient webClient = WebClient.builder()
			.baseUrl(BASE_URL)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-gms-key")
			.exchangeFunction(exchange)
			.build();
		CatchUpSummaryProperties properties = new CatchUpSummaryProperties();
		properties.setSttEnabled(true);
		properties.setTranscriptionPath("/audio/transcriptions");
		properties.setTranscriptionModel("whisper-1");
		return new GmsSpeechToTextGateway(webClient, new ObjectMapper(), properties);
	}

	private static final class RecordingExchangeFunction implements ExchangeFunction {

		private HttpMethod method;
		private String url;
		private String authorization;
		private String contentType;
		private long contentLength;
		private String body;
		private byte[] bodyBytes;
		private HttpStatus responseStatus = HttpStatus.OK;
		private String responseBody = "{\"text\":\"상품은 정상 작동합니다.\"}";

		@Override
		public Mono<ClientResponse> exchange(ClientRequest request) {
			method = request.method();
			url = request.url().toString();
			authorization = request.headers().getFirst(HttpHeaders.AUTHORIZATION);
			contentType = request.headers().getFirst(HttpHeaders.CONTENT_TYPE);
			contentLength = request.headers().getContentLength();
			bodyBytes = readBody(request);
			body = new String(bodyBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
			return Mono.just(ClientResponse.create(responseStatus)
				.header(HttpHeaders.CONTENT_TYPE, "application/json")
				.body(responseBody)
				.build());
		}

		private void respond(HttpStatus status, String body) {
			this.responseStatus = status;
			this.responseBody = body;
		}

		private static byte[] readBody(ClientRequest request) {
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
			return mockRequest.getBody().map(dataBuffer -> {
				byte[] bytes = new byte[dataBuffer.readableByteCount()];
				dataBuffer.read(bytes);
				return bytes;
			}).reduce(new byte[0], (left, right) -> {
				byte[] combined = java.util.Arrays.copyOf(left, left.length + right.length);
				System.arraycopy(right, 0, combined, left.length, right.length);
				return combined;
			}).block();
		}
	}
}
