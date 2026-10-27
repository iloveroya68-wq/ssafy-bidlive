package com.ssafy.bidlive.product.application.port.out;

import java.util.List;

import com.ssafy.bidlive.product.domain.CategoryScore;

/**
 * AI가 돌려준 카테고리별 위험도와 근거이며, 판정 자체는 도메인 정책이 임계값으로 결정합니다.
 */
public record ModerationJudgement(
	List<CategoryScore> scores,
	String reason
) {

	public ModerationJudgement {
		scores = scores == null ? List.of() : List.copyOf(scores);
		reason = reason == null ? "" : reason.strip();
	}
}
