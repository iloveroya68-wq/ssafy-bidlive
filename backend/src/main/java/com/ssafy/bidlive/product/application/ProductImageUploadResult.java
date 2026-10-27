package com.ssafy.bidlive.product.application;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.product.domain.ProductImageMetadata;

public record ProductImageUploadResult(
	String imageId,
	String imageUrl,
	OffsetDateTime createdAt
) {

	public static ProductImageUploadResult from(ProductImageMetadata metadata) {
		return new ProductImageUploadResult(
			metadata.imageId().toString(),
			metadata.imageUrl(),
			metadata.createdAt()
		);
	}
}
