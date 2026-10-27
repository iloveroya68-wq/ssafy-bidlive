package com.ssafy.bidlive.product.application;

/** 상품 정보 또는 업로드 이미지가 경매방 생성 조건을 충족하지 못했음을 나타냅니다. */
public class ProductRegistrationException extends RuntimeException {

	public ProductRegistrationException() {
		super("INVALID_PRODUCT_REQUEST");
	}

	public ProductRegistrationException(Throwable cause) {
		super("INVALID_PRODUCT_REQUEST", cause);
	}
}
