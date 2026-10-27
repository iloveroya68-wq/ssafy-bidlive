package com.ssafy.bidlive.product.application;

/**
 * 상품의 현재 공개 값입니다. 부분 수정에서 "유지"를 계산하려면 현재 값이 필요하고,
 * 수정 결과를 그대로 응답에 실을 수 있어야 하므로 대표 이미지까지 함께 담습니다.
 */
public record ProductSnapshot(
	Long productId,
	Long sellerMemberId,
	String title,
	String description,
	String categoryCode,
	String imageUrl
) {
}
