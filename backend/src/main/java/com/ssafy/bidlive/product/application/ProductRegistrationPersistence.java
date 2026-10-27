package com.ssafy.bidlive.product.application;

/** 상품 생성과 업로드 이미지 연결을 원자적으로 처리하는 영속성 계약입니다. */
public interface ProductRegistrationPersistence {

	boolean existsActiveCategory(String categoryCode);

	Long saveProductWithOwnedImage(ProductRegistrationCommand command);
}
