package com.ssafy.bidlive.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FilterResultTest {

	@Test
	void createsUnmaskedResult() {
		FilterResult result = new FilterResult(
			"상품 상태가 궁금합니다.",
			Set.of()
		);

		assertThat(result.filteredContent())
			.isEqualTo("상품 상태가 궁금합니다.");
		assertThat(result.maskedFields()).isEmpty();
		assertThat(result.masked()).isFalse();
	}

	@Test
	void createsMaskedResult() {
		FilterResult result = new FilterResult(
			"카톡 ******으로 연락 주세요.",
			Set.of(MaskingCategory.CONTACT)
		);

		assertThat(result.filteredContent())
			.isEqualTo("카톡 ******으로 연락 주세요.");
		assertThat(result.maskedFields())
			.containsExactly(MaskingCategory.CONTACT);
		assertThat(result.masked()).isTrue();
	}

	@Test
	void treatsMaskedSensitiveContentAsBlocked() {
		FilterResult result = new FilterResult(
			"연락처 *************",
			Set.of(MaskingCategory.CONTACT)
		);

		assertThat(result.blocked()).isTrue();
	}

	@Test
	void treatsNormalContentAsNotBlocked() {
		FilterResult result = new FilterResult(
			"일반 채팅입니다.",
			Set.of()
		);

		assertThat(result.blocked()).isFalse();
	}

	@Test
	void copiesMaskedFieldsDefensively() {
		Set<MaskingCategory> categories =
			EnumSet.of(MaskingCategory.CONTACT);

		FilterResult result = new FilterResult(
			"필터링된 내용",
			categories
		);

		categories.add(MaskingCategory.ACCOUNT);

		assertThat(result.maskedFields())
			.containsExactly(MaskingCategory.CONTACT);
	}

	@Test
	void exposesImmutableMaskedFields() {
		FilterResult result = new FilterResult(
			"필터링된 내용",
			Set.of(MaskingCategory.CONTACT)
		);

		assertThatThrownBy(
			() -> result.maskedFields()
				.add(MaskingCategory.ACCOUNT)
		)
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsNullFilteredContent() {
		assertThatThrownBy(
			() -> new FilterResult(null, Set.of())
		)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("filteredContent");
	}

	@Test
	void rejectsNullMaskedFields() {
		assertThatThrownBy(
			() -> new FilterResult("내용", null)
		)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("maskedFields");
	}
}
