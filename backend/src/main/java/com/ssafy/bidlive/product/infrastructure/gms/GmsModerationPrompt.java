package com.ssafy.bidlive.product.infrastructure.gms;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.ssafy.bidlive.product.domain.InspectionCategory;

/**
 * 검열 프롬프트와 응답 JSON 스키마를 만드는 도우미입니다.
 *
 * <p>GMS에 별도 Moderation 모델이 없어 멀티모달 텍스트 모델에 판정을 맡긴다. 그래서 카테고리
 * 목록과 출력 형식을 스키마로 못 박아 모델이 임의 형식이나 임의 카테고리를 만들지 못하게 한다.
 */
final class GmsModerationPrompt {

	static final String SCHEMA_NAME = "product_moderation";

	private GmsModerationPrompt() {
	}

	static String systemInstruction() {
		String categories = InspectionCategory.all().stream()
			.map(category -> "- %s: %s".formatted(category.name(), category.description()))
			.collect(Collectors.joining("\n"));

		return """
			당신은 중고 경매 플랫폼의 상품 검열 심사자입니다.
			입력으로 받은 상품 텍스트 또는 이미지가 아래 카테고리에 해당하는지 판단하세요.

			%s

			규칙:
			- 각 카테고리마다 0.0에서 1.0 사이의 위험도를 매기세요. 해당하지 않으면 0.0입니다.
			- 위 목록에 없는 카테고리를 새로 만들지 마세요.
			- reason에는 판단 근거를 한국어 한 문장으로 쓰세요.
			- 판단이 어려우면 위험도를 임의로 낮추지 말고 애매한 정도를 그대로 반영하세요.
			- 지정된 JSON 스키마만 출력하고 다른 텍스트는 쓰지 마세요.
			""".formatted(categories);
	}

	static String textUserInstruction() {
		return "다음 상품 텍스트를 검열하세요.";
	}

	static String imageUserInstruction() {
		return "다음 상품 이미지를 검열하세요.";
	}

	/**
	 * 카테고리 점수를 배열이 아니라 고정 키 객체로 요구한다. 모든 카테고리를 필수 키로 두면
	 * 모델이 일부 카테고리를 빠뜨려 점수가 없는 축이 생기는 것을 막을 수 있다.
	 */
	static Map<String, Object> responseFormat() {
		Map<String, Object> scoreProperties = new LinkedHashMap<>();
		for (InspectionCategory category : InspectionCategory.all()) {
			scoreProperties.put(category.name(), Map.of(
				"type", "number",
				"minimum", 0,
				"maximum", 1
			));
		}

		Map<String, Object> scoresSchema = new LinkedHashMap<>();
		scoresSchema.put("type", "object");
		scoresSchema.put("properties", scoreProperties);
		scoresSchema.put("required", InspectionCategory.all().stream()
			.map(InspectionCategory::name)
			.toList());
		scoresSchema.put("additionalProperties", false);

		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", Map.of(
			"scores", scoresSchema,
			"reason", Map.of("type", "string")
		));
		schema.put("required", java.util.List.of("scores", "reason"));
		schema.put("additionalProperties", false);

		return Map.of(
			"type", "json_schema",
			"json_schema", Map.of(
				"name", SCHEMA_NAME,
				"strict", true,
				"schema", schema
			)
		);
	}
}
