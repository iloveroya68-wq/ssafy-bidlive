package com.ssafy.bidlive.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 부분 수정의 "유지" 병합, 소유자 검증, 이미지 교체 실패 처리를 검증한다. */
class ProductUpdateServiceTest {

	private static final long PRODUCT_ID = 501L;
	private static final long SELLER_ID = 25L;

	private ProductUpdatePersistence persistence;
	private ProductUpdateService service;

	@BeforeEach
	void setUp() {
		persistence = mock(ProductUpdatePersistence.class);
		service = new ProductUpdateService(persistence);
		when(persistence.findSnapshot(PRODUCT_ID)).thenReturn(Optional.of(new ProductSnapshot(
			PRODUCT_ID, SELLER_ID, "원래 제목", "원래 설명", "ELECTRONICS", "https://cdn/old.png"
		)));
		when(persistence.existsActiveCategory(anyString())).thenReturn(true);
		when(persistence.replaceRepresentativeImage(anyLong(), anyLong(), anyString()))
			.thenReturn(true);
	}

	@Test
	void keepsUnsentFields() {
		ProductSnapshot result = service.update(command(null, "바뀐 설명", null, null));

		assertThat(result.title()).isEqualTo("원래 제목");
		assertThat(result.description()).isEqualTo("바뀐 설명");
		assertThat(result.categoryCode()).isEqualTo("ELECTRONICS");
		assertThat(result.imageUrl()).isEqualTo("https://cdn/old.png");
		verify(persistence).updateFields(PRODUCT_ID, "원래 제목", "바뀐 설명", "ELECTRONICS");
	}

	/** 경매 조건만 고치는 요청도 이 서비스를 지나간다 — 변경 없음은 오류가 아니다 */
	@Test
	void returnsCurrentValuesWhenNothingToChange() {
		ProductSnapshot result = service.update(command(null, null, null, null));

		assertThat(result.title()).isEqualTo("원래 제목");
		verify(persistence, never()).updateFields(anyLong(), anyString(), anyString(), anyString());
	}

	@Test
	void rejectsMissingProduct() {
		when(persistence.findSnapshot(PRODUCT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(command("새 제목", null, null, null)))
			.satisfies(e -> assertThat(reasonOf(e))
				.isEqualTo(ProductUpdateException.Reason.PRODUCT_NOT_FOUND));
	}

	@Test
	void rejectsMemberWhoIsNotTheOwner() {
		assertThatThrownBy(() -> service.update(new ProductUpdateCommand(
			PRODUCT_ID, 999L, "새 제목", null, null, null
		)))
			.satisfies(e -> assertThat(reasonOf(e))
				.isEqualTo(ProductUpdateException.Reason.NOT_PRODUCT_OWNER));
	}

	@Test
	void rejectsInactiveOrUnknownCategory() {
		when(persistence.existsActiveCategory("NOPE")).thenReturn(false);

		assertThatThrownBy(() -> service.update(command(null, null, "NOPE", null)))
			.satisfies(e -> assertThat(reasonOf(e))
				.isEqualTo(ProductUpdateException.Reason.INVALID_REQUEST));
	}

	@Test
	void uppercasesCategoryCode() {
		service.update(command(null, null, "electronics", null));

		verify(persistence).updateFields(PRODUCT_ID, "원래 제목", "원래 설명", "ELECTRONICS");
	}

	/** 빈 문자열은 "유지"와 구분해 거절한다 — `title`·`description`이 DB NOT NULL이다 */
	@Test
	void rejectsBlankValuesInsteadOfTreatingThemAsUnchanged() {
		assertThatThrownBy(() -> service.update(command("   ", null, null, null)))
			.satisfies(e -> assertThat(reasonOf(e))
				.isEqualTo(ProductUpdateException.Reason.INVALID_REQUEST));
		assertThatThrownBy(() -> service.update(command(null, "", null, null)))
			.satisfies(e -> assertThat(reasonOf(e))
				.isEqualTo(ProductUpdateException.Reason.INVALID_REQUEST));
	}

	@Test
	void rejectsTooLongTitle() {
		assertThatThrownBy(() -> service.update(command("가".repeat(101), null, null, null)))
			.satisfies(e -> assertThat(reasonOf(e))
				.isEqualTo(ProductUpdateException.Reason.INVALID_REQUEST));
	}

	@Test
	void replacesImageWhenUrlChanges() {
		ProductSnapshot result = service.update(command(null, null, null, "https://cdn/new.png"));

		verify(persistence).replaceRepresentativeImage(
			PRODUCT_ID, SELLER_ID, "https://cdn/new.png"
		);
		assertThat(result.imageUrl()).isEqualTo("https://cdn/new.png");
	}

	/** 같은 URL을 다시 보내면 교체할 이유가 없다 — 떼었다 붙이면 멀쩡한 연결을 흔든다 */
	@Test
	void skipsImageReplacementWhenUrlIsUnchanged() {
		service.update(command(null, null, null, "https://cdn/old.png"));

		verify(persistence, never()).replaceRepresentativeImage(anyLong(), anyLong(), anyString());
	}

	@Test
	void rejectsImageThatCannotBeClaimed() {
		when(persistence.replaceRepresentativeImage(anyLong(), anyLong(), anyString()))
			.thenReturn(false);

		assertThatThrownBy(() -> service.update(command(null, null, null, "https://cdn/x.png")))
			.satisfies(e -> assertThat(reasonOf(e))
				.isEqualTo(ProductUpdateException.Reason.IMAGE_NOT_CLAIMABLE));
	}

	private static ProductUpdateCommand command(
		String title,
		String description,
		String categoryCode,
		String imageUrl
	) {
		return new ProductUpdateCommand(
			PRODUCT_ID, SELLER_ID, title, description, categoryCode, imageUrl
		);
	}

	private static ProductUpdateException.Reason reasonOf(Throwable exception) {
		return ((ProductUpdateException) exception).getReason();
	}
}
