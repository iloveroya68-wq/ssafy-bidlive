package com.ssafy.bidlive.product.application.port.out;

import com.ssafy.bidlive.product.domain.ProductImageFile;

/** 검증이 끝난 상품 이미지를 외부 저장소에 저장하는 포트입니다. */
public interface ProductImageStorageGateway {

	String upload(String key, ProductImageFile image);

	void delete(String key);
}
