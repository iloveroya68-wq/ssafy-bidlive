package com.ssafy.bidlive.product.domain;

import java.util.Locale;
import java.util.Optional;

/** 허용하는 상품 이미지 형식과 실제 파일 시그니처 판별 규칙입니다. */
public enum ProductImageType {
	JPEG("image/jpeg", "jpg"),
	PNG("image/png", "png"),
	WEBP("image/webp", "webp");

	private final String contentType;
	private final String extension;

	ProductImageType(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	public String contentType() {
		return contentType;
	}

	public String extension() {
		return extension;
	}

	public boolean matchesContentType(String declaredContentType) {
		if (declaredContentType == null) {
			return false;
		}
		String normalized = declaredContentType.split(";", 2)[0]
			.trim()
			.toLowerCase(Locale.ROOT);
		return contentType.equals(normalized)
			|| (this == JPEG && "image/jpg".equals(normalized));
	}

	public static Optional<ProductImageType> detect(byte[] content) {
		if (hasPrefix(content, 0xFF, 0xD8, 0xFF)) {
			return Optional.of(JPEG);
		}
		if (hasPrefix(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
			return Optional.of(PNG);
		}
		if (content != null
			&& content.length >= 12
			&& hasAscii(content, 0, "RIFF")
			&& hasAscii(content, 8, "WEBP")) {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}

	private static boolean hasPrefix(byte[] content, int... expected) {
		if (content == null || content.length < expected.length) {
			return false;
		}
		for (int index = 0; index < expected.length; index++) {
			if (Byte.toUnsignedInt(content[index]) != expected[index]) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasAscii(byte[] content, int offset, String expected) {
		for (int index = 0; index < expected.length(); index++) {
			if (content[offset + index] != (byte) expected.charAt(index)) {
				return false;
			}
		}
		return true;
	}
}
