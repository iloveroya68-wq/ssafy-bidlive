package com.ssafy.bidlive.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 검열 대상이 최소 하나 있어야 하고, 같은 콘텐츠가 같은 캐시 키를 갖는지 검증합니다. */
class InspectionContentTest {

	@Test
	void rejectsContentWithNothingToInspect() {
		assertThatThrownBy(() -> new InspectionContent(null, null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new InspectionContent("  ", "\t", List.of("  ")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void acceptsAnySingleKindOfContent() {
		assertThat(new InspectionContent("제목", null, null).hasText()).isTrue();
		assertThat(new InspectionContent(null, "설명", null).hasText()).isTrue();
		assertThat(new InspectionContent(null, null, List.of("https://img/1.jpg")).hasText())
			.isFalse();
	}

	/** 이미지 순서만 다른 요청은 같은 콘텐츠이므로 캐시가 재사용되어야 한다. */
	@Test
	void producesSameHashRegardlessOfImageOrderAndDuplicates() {
		InspectionContent first = new InspectionContent(
			"제목", "설명", List.of("https://img/a.jpg", "https://img/b.jpg"));
		InspectionContent second = new InspectionContent(
			"제목 ", " 설명", List.of("https://img/b.jpg", "https://img/a.jpg", "https://img/a.jpg"));

		assertThat(first.contentHash()).isEqualTo(second.contentHash());
		assertThat(first.imageUrls()).containsExactly("https://img/a.jpg", "https://img/b.jpg");
	}

	/** 필드 경계가 없으면 제목·설명 조합이 달라도 같은 해시가 될 수 있다. */
	@Test
	void separatesFieldsWhenHashing() {
		InspectionContent first = new InspectionContent("ab", "c", null);
		InspectionContent second = new InspectionContent("a", "bc", null);

		assertThat(first.contentHash()).isNotEqualTo(second.contentHash());
	}

	@Test
	void joinsTitleAndDescriptionForSingleTextInspection() {
		assertThat(new InspectionContent("제목", "설명", null).text()).isEqualTo("제목\n\n설명");
		assertThat(new InspectionContent("제목", null, null).text()).isEqualTo("제목");
		assertThat(new InspectionContent(null, "설명", null).text()).isEqualTo("설명");
	}

	@Test
	void dropsBlankAndNullImageUrls() {
		InspectionContent content = new InspectionContent(
			"제목", null, Arrays.asList("https://img/a.jpg", null, "   "));

		assertThat(content.imageUrls()).containsExactly("https://img/a.jpg");
	}
}
