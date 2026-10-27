package com.ssafy.bidlive.product.application;

import com.ssafy.bidlive.product.domain.ProductImageUploadErrorCode;

public class ProductImageUploadException extends RuntimeException {

	private final ProductImageUploadErrorCode errorCode;

	public ProductImageUploadException(ProductImageUploadErrorCode errorCode) {
		this(errorCode, null);
	}

	public ProductImageUploadException(
		ProductImageUploadErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public ProductImageUploadErrorCode getErrorCode() {
		return errorCode;
	}
}
