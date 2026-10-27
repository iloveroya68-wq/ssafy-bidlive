package com.ssafy.bidlive.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MaskedRangeTest {

	@Test
	void createsValidRange() {
		MaskedRange range = new MaskedRange(
			3,
			9,
			MaskingCategory.CONTACT
		);

		assertThat(range.startInclusive()).isEqualTo(3);
		assertThat(range.endExclusive()).isEqualTo(9);
		assertThat(range.category()).isEqualTo(
			MaskingCategory.CONTACT
		);
	}

	@Test
	void rejectsNegativeStartPosition() {
		assertThatThrownBy(
			() -> new MaskedRange(
				-1,
				3,
				MaskingCategory.CONTACT
			)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("startInclusive는 0 이상이어야 합니다.");
	}

	@Test
	void rejectsEmptyRange() {
		assertThatThrownBy(
			() -> new MaskedRange(
				3,
				3,
				MaskingCategory.CONTACT
			)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(
				"endExclusive는 startInclusive보다 커야 합니다."
			);
	}

	@Test
	void rejectsReversedRange() {
		assertThatThrownBy(
			() -> new MaskedRange(
				5,
				3,
				MaskingCategory.CONTACT
			)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(
				"endExclusive는 startInclusive보다 커야 합니다."
			);
	}

	@Test
	void rejectsNullCategory() {
		assertThatThrownBy(
			() -> new MaskedRange(3, 9, null)
		)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("category");
	}
}
