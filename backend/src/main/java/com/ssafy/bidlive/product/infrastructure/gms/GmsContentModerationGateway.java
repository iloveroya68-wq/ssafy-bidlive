package com.ssafy.bidlive.product.infrastructure.gms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.product.application.port.out.ContentModerationException;
import com.ssafy.bidlive.product.application.port.out.ContentModerationGateway;
import com.ssafy.bidlive.product.application.port.out.ModerationFailureKind;
import com.ssafy.bidlive.product.application.port.out.ModerationJudgement;
import com.ssafy.bidlive.product.domain.CategoryScore;
import com.ssafy.bidlive.product.domain.InspectionCategory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * GMS의 OpenAI 호환 Chat Completions API로 상품 텍스트·이미지 검열을 요청하는 어댑터입니다.
 *
 * <p>이미지는 확정된 방식대로 {@code image_url} 파트에 URL을 그대로 전달한다. 응답은
 * {@code response_format}의 JSON 스키마로 강제하되, 그래도 형식을 어길 수 있으므로 파싱 실패를
 * 별도 실패 원인으로 구분해 상위에서 보류 판정이 되게 한다.
 *
 * <p>실패 원인은 네 가지로만 좁혀 올린다. 어떤 경로로도 "점수 없는 정상 판정"을 만들지 않는 것이
 * 이 어댑터의 계약이다.
 */
public class GmsContentModerationGateway implements ContentModerationGateway {

	private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
	private static final String INSUFFICIENT_QUOTA_MARKER = "insufficient_quota";

	private final WebClient gmsWebClient;
	private final ObjectMapper objectMapper;
	private final GmsProperties gmsProperties;

	public GmsContentModerationGateway(
		WebClient gmsWebClient,
		ObjectMapper objectMapper,
		GmsProperties gmsProperties
	) {
		this.gmsWebClient = gmsWebClient;
		this.objectMapper = objectMapper;
		this.gmsProperties = gmsProperties;
	}

	@Override
	public ModerationJudgement inspectText(String text) {
		return request(List.of(Map.of(
			"role", "user",
			"content", List.of(Map.of(
				"type", "text",
				"text", GmsModerationPrompt.textUserInstruction() + "\n\n" + text
			))
		)));
	}

	@Override
	public ModerationJudgement inspectImage(String imageUrl) {
		return request(List.of(Map.of(
			"role", "user",
			"content", List.of(
				Map.of("type", "text", "text", GmsModerationPrompt.imageUserInstruction()),
				Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
			)
		)));
	}

	private ModerationJudgement request(List<Map<String, Object>> userMessages) {
		List<Map<String, Object>> messages = new ArrayList<>();
		messages.add(Map.of(
			"role", "system",
			"content", GmsModerationPrompt.systemInstruction()
		));
		messages.addAll(userMessages);

		Map<String, Object> body = Map.of(
			"model", gmsProperties.getInspectionModel(),
			"messages", messages,
			"temperature", 0,
			"response_format", GmsModerationPrompt.responseFormat()
		);

		return parse(call(body));
	}

	/**
	 * 재시도는 전송 오류에만 적용한다. 4xx는 다시 보내도 같은 결과이고 호출마다 Credit이
	 * 차감되므로 재시도가 낭비다.
	 */
	private String call(Map<String, Object> body) {
		WebClientException lastFailure = null;

		for (int attempt = 0; attempt <= gmsProperties.getMaxRetries(); attempt++) {
			try {
				return gmsWebClient.post()
					.uri(CHAT_COMPLETIONS_PATH)
					.contentType(MediaType.APPLICATION_JSON)
					.bodyValue(body)
					.retrieve()
					.bodyToMono(String.class)
					.block();
			} catch (WebClientResponseException exception) {
				throw translate(exception);
			} catch (WebClientRequestException exception) {
				lastFailure = exception;
			}
		}

		throw new ContentModerationException(
			ModerationFailureKind.TRANSPORT,
			"GMS moderation request failed after retries",
			lastFailure
		);
	}

	/** 예외 메시지에는 GMS 키가 담긴 헤더를 넣지 않고 상태 코드만 남긴다. */
	private ContentModerationException translate(WebClientResponseException exception) {
		int status = exception.getStatusCode().value();
		String responseBody = exception.getResponseBodyAsString();

		if (status == HttpStatus.TOO_MANY_REQUESTS.value()
			&& responseBody.contains(INSUFFICIENT_QUOTA_MARKER)) {
			return new ContentModerationException(
				ModerationFailureKind.CREDIT_EXHAUSTED,
				"GMS credit is exhausted"
			);
		}
		if (status == HttpStatus.PAYMENT_REQUIRED.value()) {
			return new ContentModerationException(
				ModerationFailureKind.CREDIT_EXHAUSTED,
				"GMS credit is exhausted"
			);
		}
		if (status == HttpStatus.GATEWAY_TIMEOUT.value()
			|| status == HttpStatus.REQUEST_TIMEOUT.value()) {
			return new ContentModerationException(
				ModerationFailureKind.TIMEOUT,
				"GMS moderation request timed out with status " + status
			);
		}

		return new ContentModerationException(
			ModerationFailureKind.TRANSPORT,
			"GMS moderation request failed with status " + status
		);
	}

	private ModerationJudgement parse(String rawResponse) {
		if (rawResponse == null || rawResponse.isBlank()) {
			throw invalidResponse("GMS returned an empty moderation response");
		}

		JsonNode content;
		try {
			JsonNode completion = objectMapper.readTree(rawResponse);
			JsonNode messageContent = completion.path("choices").path(0)
				.path("message").path("content");
			if (!messageContent.isTextual()) {
				throw invalidResponse("GMS moderation response has no message content");
			}
			content = objectMapper.readTree(messageContent.asText());
		} catch (JsonProcessingException exception) {
			throw invalidResponse("GMS moderation response is not valid JSON");
		}

		JsonNode scores = content.path("scores");
		if (!scores.isObject()) {
			throw invalidResponse("GMS moderation response has no scores object");
		}

		List<CategoryScore> categoryScores = new ArrayList<>();
		for (InspectionCategory category : InspectionCategory.all()) {
			JsonNode score = scores.path(category.name());
			if (!score.isNumber()) {
				throw invalidResponse(
					"GMS moderation response misses score for " + category.name());
			}
			categoryScores.add(new CategoryScore(category, clamp(score.asDouble())));
		}

		return new ModerationJudgement(categoryScores, content.path("reason").asText(""));
	}

	/** 스키마 범위를 벗어난 점수가 와도 판정 자체를 버리지 않고 유효 범위로 접는다. */
	private double clamp(double score) {
		return Math.max(0.0, Math.min(1.0, score));
	}

	private ContentModerationException invalidResponse(String message) {
		return new ContentModerationException(ModerationFailureKind.INVALID_RESPONSE, message);
	}
}
