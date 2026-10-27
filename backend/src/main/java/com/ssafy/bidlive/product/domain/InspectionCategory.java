package com.ssafy.bidlive.product.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 중고 경매 상품 검열에서 판정하는 위험 카테고리이며, 이름이 그대로 AI 응답 스키마의 키가 됩니다.
 *
 * <p>카테고리를 코드 상수로 고정하는 이유는 모델이 임의 카테고리를 새로 만들어 보내도 집계
 * 대상에서 제외해 판정 기준이 흔들리지 않게 하기 위해서다.
 */
public enum InspectionCategory {

	ILLEGAL_GOODS("법령상 거래가 금지된 물품"),
	COUNTERFEIT("위조품 또는 정품을 사칭한 물품"),
	WEAPONS("무기류 및 그 부품"),
	DRUGS("마약류, 의약품 또는 유해 화학물질"),
	ADULT("성적으로 노골적인 표현이나 성인용품"),
	VIOLENCE("폭력적이거나 혐오감을 주는 표현"),
	HATE("특정 집단에 대한 혐오 표현"),
	PERSONAL_INFO("타인의 개인정보 노출"),
	SPAM("광고, 외부 거래 유도 또는 사기 정황");

	private final String description;

	InspectionCategory(String description) {
		this.description = description;
	}

	public static Optional<InspectionCategory> from(String name) {
		if (name == null || name.isBlank()) {
			return Optional.empty();
		}

		String normalized = name.strip().toUpperCase();
		return Arrays.stream(values())
			.filter(category -> category.name().equals(normalized))
			.findFirst();
	}

	public static List<InspectionCategory> all() {
		return List.of(values());
	}

	public String description() {
		return description;
	}
}
