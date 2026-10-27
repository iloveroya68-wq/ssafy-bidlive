package com.ssafy.bidlive.product.infrastructure.gms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.product.application.port.out.ContentModerationException;
import com.ssafy.bidlive.product.application.port.out.ModerationFailureKind;
import com.ssafy.bidlive.product.application.port.out.ModerationJudgement;
import com.ssafy.bidlive.product.domain.CategoryScore;
import com.ssafy.bidlive.product.domain.InspectionCategory;
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

/**
 * OpenAI 호환 요청 형식과 JSON 스키마 강제, 그리고 스키마 위반·Credit 소진·전송 오류가
 * 각각 다른 실패 원인으로 올라가는지 검증합니다.
 */
class GmsContentModerationGatewayTest {

	private static final String BASE_URL = "https://gms.test/v1";

	private final StubExchangeFunction exchangeFunction = new StubExchangeFunction();
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final GmsContentModerationGateway gateway = new GmsContentModerationGateway(
		WebClient.builder().baseUrl(BASE_URL).exchangeFunction(exchangeFunction).build(),
		objectMapper,
		properties()
	);

	@Test
	void sendsChatCompletionRequestWithJsonSchemaResponseFormat() {
		exchangeFunction.enqueueJson(HttpStatus.OK, completion(allScores(0.0), "정상"));

		gateway.inspectText("판매합니다");

		RecordedRequest request = exchangeFunction.recorded().get(0);
		assertThat(request.method()).isEqualTo(HttpMethod.POST);
		assertThat(request.url()).isEqualTo(BASE_URL + "/chat/completions");
		assertThat(request.body()).contains(
			"\"model\":\"test-vision-model\"",
			"\"type\":\"json_schema\"",
			"\"name\":\"product_moderation\"",
			"\"strict\":true",
			"\"temperature\":0"
		);
	}

	/** 이미지는 확정된 방식대로 image_url 파트로 URL을 그대로 전달한다. */
	@Test
	void sendsImageUrlPartForImageInspection() {
		exchangeFunction.enqueueJson(HttpStatus.OK, completion(allScores(0.0), "정상"));

		gateway.inspectImage("https://img/a.jpg");

		assertThat(exchangeFunction.recorded().get(0).body()).contains(
			"\"type\":\"image_url\"",
			"\"url\":\"https://img/a.jpg\""
		);
	}

	@Test
	void parsesEveryCategoryScoreFromResponse() {
		exchangeFunction.enqueueJson(HttpStatus.OK, completion(
			scoresWith(InspectionCategory.ILLEGAL_GOODS, 0.9), "위험"));

		ModerationJudgement judgement = gateway.inspectText("불법 물품");

		assertThat(judgement.reason()).isEqualTo("위험");
		assertThat(judgement.scores()).hasSize(InspectionCategory.all().size());
		assertThat(judgement.scores())
			.filteredOn(score -> score.category() == InspectionCategory.ILLEGAL_GOODS)
			.singleElement()
			.extracting(CategoryScore::score)
			.isEqualTo(0.9);
	}

	/** 스키마 범위를 벗어난 점수가 와도 판정을 버리지 않고 유효 범위로 접는다. */
	@Test
	void clampsScoreOutsideNormalizedRange() {
		exchangeFunction.enqueueJson(HttpStatus.OK, completion(
			scoresWith(InspectionCategory.ILLEGAL_GOODS, 1.7), "위험"));

		assertThat(gateway.inspectText("불법 물품").scores())
			.filteredOn(score -> score.category() == InspectionCategory.ILLEGAL_GOODS)
			.singleElement()
			.extracting(CategoryScore::score)
			.isEqualTo(1.0);
	}

	@Test
	void reportsInvalidResponseWhenContentIsNotJson() {
		exchangeFunction.enqueueJson(HttpStatus.OK, """
			{"choices":[{"message":{"content":"검열 결과는 통과입니다"}}]}
			""");

		assertFailureKind(() -> gateway.inspectText("판매합니다"),
			ModerationFailureKind.INVALID_RESPONSE);
	}

	@Test
	void reportsInvalidResponseWhenCategoryScoreIsMissing() {
		exchangeFunction.enqueueJson(HttpStatus.OK,
			completion(Map.of(InspectionCategory.ILLEGAL_GOODS.name(), 0.1), "정상"));

		assertFailureKind(() -> gateway.inspectText("판매합니다"),
			ModerationFailureKind.INVALID_RESPONSE);
	}

	@Test
	void reportsInvalidResponseWhenChoicesAreEmpty() {
		exchangeFunction.enqueueJson(HttpStatus.OK, "{\"choices\":[]}");

		assertFailureKind(() -> gateway.inspectText("판매합니다"),
			ModerationFailureKind.INVALID_RESPONSE);
	}

	@Test
	void reportsCreditExhaustionForInsufficientQuota() {
		exchangeFunction.enqueue(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
			.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
			.body("{\"error\":{\"code\":\"insufficient_quota\"}}")
			.build());

		assertFailureKind(() -> gateway.inspectText("판매합니다"),
			ModerationFailureKind.CREDIT_EXHAUSTED);
	}

	@Test
	void reportsCreditExhaustionForPaymentRequired() {
		exchangeFunction.enqueue(ClientResponse.create(HttpStatus.PAYMENT_REQUIRED).build());

		assertFailureKind(() -> gateway.inspectText("판매합니다"),
			ModerationFailureKind.CREDIT_EXHAUSTED);
	}

	/** 사용량 초과이지만 Credit 소진이 아니면 재시도 가능한 전송 오류로 다룬다. */
	@Test
	void reportsTransportErrorForRateLimitWithoutQuotaMarker() {
		exchangeFunction.enqueue(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
			.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
			.body("{\"error\":{\"code\":\"rate_limit_exceeded\"}}")
			.build());

		assertFailureKind(() -> gateway.inspectText("판매합니다"),
			ModerationFailureKind.TRANSPORT);
	}

	@Test
	void reportsTimeoutForGatewayTimeoutStatus() {
		exchangeFunction.enqueue(ClientResponse.create(HttpStatus.GATEWAY_TIMEOUT).build());

		assertFailureKind(() -> gateway.inspectText("판매합니다"), ModerationFailureKind.TIMEOUT);
	}

	@Test
	void reportsTransportErrorForServerError() {
		exchangeFunction.enqueue(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());

		assertFailureKind(() -> gateway.inspectText("판매합니다"), ModerationFailureKind.TRANSPORT);
	}

	/** 4xx는 다시 보내도 결과가 같고 호출마다 Credit이 차감되므로 재시도하지 않는다. */
	@Test
	void doesNotRetryClientErrors() {
		exchangeFunction.enqueue(ClientResponse.create(HttpStatus.BAD_REQUEST).build());

		assertFailureKind(() -> gateway.inspectText("판매합니다"), ModerationFailureKind.TRANSPORT);
		assertThat(exchangeFunction.recorded()).hasSize(1);
	}

	/** 실패 메시지에 GMS Key가 새지 않아야 한다. */
	@Test
	void neverExposesApiKeyInFailureMessage() {
		exchangeFunction.enqueue(ClientResponse.create(HttpStatus.UNAUTHORIZED).build());

		assertThatThrownBy(() -> gateway.inspectText("판매합니다"))
			.isInstanceOf(ContentModerationException.class)
			.hasMessageNotContaining("test-gms-key");
	}

	private void assertFailureKind(Runnable call, ModerationFailureKind expected) {
		assertThatThrownBy(call::run)
			.isInstanceOf(ContentModerationException.class)
			.extracting(exception -> ((ContentModerationException) exception).getKind())
			.isEqualTo(expected);
	}

	private GmsProperties properties() {
		GmsProperties properties = new GmsProperties();
		properties.setBaseUrl(BASE_URL);
		properties.setApiKey("test-gms-key");
		properties.setInspectionModel("test-vision-model");
		properties.setMaxRetries(0);
		return properties;
	}

	private Map<String, Double> allScores(double defaultScore) {
		Map<String, Double> scores = new LinkedHashMap<>();
		InspectionCategory.all().forEach(category -> scores.put(category.name(), defaultScore));
		return scores;
	}

	private Map<String, Double> scoresWith(InspectionCategory category, double score) {
		Map<String, Double> scores = allScores(0.0);
		scores.put(category.name(), score);
		return scores;
	}

	/** 모델 응답의 content는 JSON 문자열이므로 이스케이프를 직접 만들지 않고 직렬화로 만든다. */
	private String completion(Map<String, Double> scores, String reason) {
		try {
			String content = objectMapper.writeValueAsString(
				Map.of("scores", scores, "reason", reason));
			return objectMapper.writeValueAsString(Map.of(
				"choices", List.of(Map.of("message", Map.of("content", content)))
			));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to build stub completion", exception);
		}
	}

	private record RecordedRequest(HttpMethod method, String url, String body) {
	}

	/** 실제 GMS 없이 응답을 미리 지정하고 전송된 요청을 그대로 기록한다. */
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
				throw new IllegalStateException("No stubbed GMS response left");
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
