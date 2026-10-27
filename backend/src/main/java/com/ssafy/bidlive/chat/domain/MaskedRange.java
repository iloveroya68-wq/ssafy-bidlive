package com.ssafy.bidlive.chat.domain;

import java.util.Objects;

public record MaskedRange(
	int startInclusive,
	int endExclusive,
	MaskingCategory category
) {

	public MaskedRange {
		if (startInclusive < 0) {
			throw new IllegalArgumentException(
				"startInclusive는 0 이상이어야 합니다."
			);
		}

		if (endExclusive <= startInclusive) {
			throw new IllegalArgumentException(
				"endExclusive는 startInclusive보다 커야 합니다."
			);
		}

		Objects.requireNonNull(category, "category");
	}
}
