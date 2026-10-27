package com.ssafy.bidlive.product.api;

/** 검열 요청에서 인증 회원 식별자를 얻을 수 없는 상태를 나타냅니다. */
public class ProductInspectionUnauthenticatedException extends RuntimeException {

	public ProductInspectionUnauthenticatedException() {
		super("Authenticated member is required for product inspection");
	}

	public ProductInspectionUnauthenticatedException(Throwable cause) {
		super("Authenticated member is required for product inspection", cause);
	}
}
