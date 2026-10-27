package com.ssafy.bidlive.product.api.dto;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.product.application.ProductImageUploadResult;

public record ProductImageUploadResponse(
	String imageId,
	String imageUrl,
	OffsetDateTime createdAt
) {

	public static ProductImageUploadResponse from(ProductImageUploadResult result) {
		return new ProductImageUploadResponse(
			result.imageId(),
			result.imageUrl(),
			result.createdAt()
		);
	}
}
