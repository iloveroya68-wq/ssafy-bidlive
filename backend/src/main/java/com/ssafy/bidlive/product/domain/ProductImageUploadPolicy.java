package com.ssafy.bidlive.product.domain;

import java.util.Set;

/** 상품 이미지의 크기, 허용 형식과 저장 key 규칙입니다. */
public record ProductImageUploadPolicy(
	long maxSizeBytes,
	Set<ProductImageType> allowedTypes,
	String keyPrefix
) {

	public ProductImageUploadPolicy {
		if (maxSizeBytes <= 0) {
			throw new IllegalArgumentException("maxSizeBytes must be positive");
		}
		allowedTypes = Set.copyOf(allowedTypes);
		if (allowedTypes.isEmpty()) {
			throw new IllegalArgumentException("allowedTypes must not be empty");
		}
		if (keyPrefix == null || keyPrefix.isBlank()) {
			throw new IllegalArgumentException("keyPrefix must not be blank");
		}
		keyPrefix = trimSlashes(keyPrefix.trim());
	}

	public boolean allows(ProductImageType type) {
		return allowedTypes.contains(type);
	}

	private static String trimSlashes(String value) {
		String result = value;
		while (result.startsWith("/")) {
			result = result.substring(1);
		}
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}
}
