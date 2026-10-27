package com.ssafy.bidlive.media.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 허용 형식과 크기 정책이 값 객체 생성 시점에 강제되는지, 형식 판별이 파라미터를 견디는지 검증합니다. */
class LiveSnapshotImageTest {

	@ParameterizedTest
	@CsvSource({
		"image/jpeg, JPEG, jpg",
		"image/png, PNG, png",
		"IMAGE/JPEG, JPEG, jpg",
		"image/jpeg; charset=binary, JPEG, jpg"
	})
	void resolvesAllowedTypeIgnoringCaseAndParameters(
		String contentType,
		SnapshotImageType expectedType,
		String expectedExtension
	) {
		assertThat(SnapshotImageType.fromContentType(contentType))
			.hasValue(expectedType);
		assertThat(expectedType.extension()).isEqualTo(expectedExtension);
	}

	@ParameterizedTest
	@ValueSource(strings = {"image/gif", "image/webp", "application/octet-stream", "text/plain", " "})
	void rejectsUnsupportedContentType(String contentType) {
		assertThat(SnapshotImageType.fromContentType(contentType)).isEmpty();
	}

	@Test
	void rejectsNullContentType() {
		assertThat(SnapshotImageType.fromContentType(null)).isEmpty();
	}

	@Test
	void rejectsEmptyContent() {
		assertThatThrownBy(() -> new LiveSnapshotImage(new byte[0], SnapshotImageType.JPEG))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new LiveSnapshotImage(null, SnapshotImageType.JPEG))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsContentLargerThanPolicy() {
		byte[] tooLarge = new byte[(int) LiveSnapshotImage.MAX_SIZE_BYTES + 1];

		assertThatThrownBy(() -> new LiveSnapshotImage(tooLarge, SnapshotImageType.JPEG))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void acceptsContentAtPolicyLimit() {
		byte[] atLimit = new byte[(int) LiveSnapshotImage.MAX_SIZE_BYTES];

		assertThat(new LiveSnapshotImage(atLimit, SnapshotImageType.JPEG).size())
			.isEqualTo(LiveSnapshotImage.MAX_SIZE_BYTES);
	}
}
