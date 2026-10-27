package com.ssafy.bidlive.product.domain;

import java.util.Optional;

/** 검열 이력의 저장과 콘텐츠 해시 기반 조회 계약입니다. */
public interface ProductInspectionRepository {

	/**
	 * 재사용 가능한 최근 판정을 찾는다. 같은 콘텐츠의 반복 요청에서 AI 호출을 건너뛰어 Credit을
	 * 아끼는 용도이므로, 보류 상태 기록은 결과에 포함하지 않는다.
	 */
	Optional<ProductInspection> findReusableByContentHash(String contentHash);

	ProductInspection save(ProductInspection inspection);
}
