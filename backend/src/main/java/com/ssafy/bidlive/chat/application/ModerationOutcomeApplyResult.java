package com.ssafy.bidlive.chat.application;

/** 판정의 최초 반영 여부와 질문 큐 제거 여부를 전달합니다. */
public record ModerationOutcomeApplyResult(
	boolean changed,
	boolean questionFiltered
) {

	public ModerationOutcomeApplyResult {
		if (!changed && questionFiltered) {
			throw new IllegalArgumentException(
				"unchanged result cannot filter question"
			);
		}
	}

	public static ModerationOutcomeApplyResult unchanged() {
		return new ModerationOutcomeApplyResult(false, false);
	}

	public static ModerationOutcomeApplyResult changed(
		boolean questionFiltered
	) {
		return new ModerationOutcomeApplyResult(true, questionFiltered);
	}
}
