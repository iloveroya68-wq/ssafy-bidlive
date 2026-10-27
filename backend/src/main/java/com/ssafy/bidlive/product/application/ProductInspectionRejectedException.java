package com.ssafy.bidlive.product.application;

import com.ssafy.bidlive.product.domain.ProductInspectionFailureCode;

/** 검열 요청이 거부된 사유를 실패 코드와 함께 API 계층으로 전달합니다. */
public class ProductInspectionRejectedException extends RuntimeException {

	private final ProductInspectionFailureCode failureCode;

	public ProductInspectionRejectedException(ProductInspectionFailureCode failureCode) {
		super("Product inspection rejected: " + failureCode);
		this.failureCode = failureCode;
	}

	public ProductInspectionRejectedException(
		ProductInspectionFailureCode failureCode,
		Throwable cause
	) {
		super("Product inspection rejected: " + failureCode, cause);
		this.failureCode = failureCode;
	}

	public ProductInspectionFailureCode getFailureCode() {
		return failureCode;
	}
}
