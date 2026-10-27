package com.ssafy.bidlive.product.application;

import java.util.Optional;

/** 상품 부분 수정과 대표 이미지 교체를 처리하는 영속성 계약입니다. */
public interface ProductUpdatePersistence {

	Optional<ProductSnapshot> findSnapshot(long productId);

	boolean existsActiveCategory(String categoryCode);

	/** 전달된 값을 그대로 씁니다 — "유지" 계산은 유스케이스가 끝낸 뒤 호출합니다. */
	void updateFields(long productId, String title, String description, String categoryCode);

	/**
	 * 대표 이미지를 판매자의 미첨부 업로드 한 건으로 교체합니다.
	 *
	 * <p>기존 이미지를 먼저 떼어내야 합니다 — `product_image`에
	 * `UNIQUE (product_id, sort_order)`가 걸려 있고 업로드는 항상 `sort_order = 1`이라,
	 * 붙이기부터 하면 제약에 걸립니다.
	 *
	 * @return 교체했으면 true, 조건에 맞는 업로드가 없으면 false
	 */
	boolean replaceRepresentativeImage(long productId, long sellerMemberId, String imageUrl);
}
