package com.ssafy.bidlive.product.application.port.out;

import com.ssafy.bidlive.product.domain.ProductImageMetadata;

/** 상품 이미지 메타데이터 저장 포트입니다. */
public interface ProductImageMetadataPersistence {

	ProductImageMetadata save(ProductImageMetadata metadata);
}
