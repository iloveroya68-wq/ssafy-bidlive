package com.ssafy.bidlive.product.domain;

import java.util.Objects;

/** 실제 시그니처 검증을 통과한 상품 이미지 바이너리입니다. */
public record ProductImageFile(byte[] content, ProductImageType type) {

	public ProductImageFile {
		Objects.requireNonNull(content, "content");
		Objects.requireNonNull(type, "type");
		if (content.length == 0) {
			throw new IllegalArgumentException("image content must not be empty");
		}
		content = content.clone();
	}

	@Override
	public byte[] content() {
		return content.clone();
	}

	public long size() {
		return content.length;
	}
}
