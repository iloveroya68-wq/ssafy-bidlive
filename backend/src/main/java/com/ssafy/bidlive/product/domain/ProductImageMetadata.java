package com.ssafy.bidlive.product.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** 경매방 생성 전 업로드된 상품 이미지의 식별자, 소유자와 저장 위치입니다. */
public record ProductImageMetadata(
	UUID imageId,
	Long ownerMemberId,
	String storageKey,
	String imageUrl,
	String contentType,
	long sizeBytes,
	OffsetDateTime createdAt
) {

	public ProductImageMetadata {
		Objects.requireNonNull(imageId, "imageId");
		Objects.requireNonNull(ownerMemberId, "ownerMemberId");
		Objects.requireNonNull(storageKey, "storageKey");
		Objects.requireNonNull(imageUrl, "imageUrl");
		Objects.requireNonNull(contentType, "contentType");
		Objects.requireNonNull(createdAt, "createdAt");
		if (ownerMemberId <= 0 || sizeBytes <= 0) {
			throw new IllegalArgumentException("ownerMemberId and sizeBytes must be positive");
		}
	}
}
