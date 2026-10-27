package com.ssafy.bidlive.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductImageTypeTest {

	@Test
	void detectsJpegPngAndWebpByFileSignature() {
		assertThat(ProductImageType.detect(jpeg())).contains(ProductImageType.JPEG);
		assertThat(ProductImageType.detect(png())).contains(ProductImageType.PNG);
		assertThat(ProductImageType.detect(webp())).contains(ProductImageType.WEBP);
	}

	@Test
	void rejectsUnknownOrTruncatedSignature() {
		assertThat(ProductImageType.detect(new byte[] {1, 2, 3})).isEmpty();
		assertThat(ProductImageType.detect(new byte[] {(byte) 0x89, 0x50})).isEmpty();
	}

	@Test
	void comparesDeclaredMimeTypeWithDetectedType() {
		assertThat(ProductImageType.JPEG.matchesContentType("image/jpeg")).isTrue();
		assertThat(ProductImageType.JPEG.matchesContentType("image/jpg")).isTrue();
		assertThat(ProductImageType.PNG.matchesContentType("image/jpeg")).isFalse();
		assertThat(ProductImageType.WEBP.matchesContentType(null)).isFalse();
	}

	private byte[] jpeg() {
		return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
	}

	private byte[] png() {
		return new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00
		};
	}

	private byte[] webp() {
		return new byte[] {
			'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0
		};
	}
}
