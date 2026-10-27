package com.ssafy.bidlive.product.application;

/**
 * 등록된 상품의 부분 수정 명령입니다. {@code null}인 필드는 현재 값을 유지합니다.
 *
 * <p>경매방 수정(ROOM-04)이 상품 쪽 값을 바꿀 때 쓰는 도메인 간 계약이며, 상품 생성이
 * {@link ProductRegistrationCommand}를 쓰는 것과 같은 자리입니다.
 */
public record ProductUpdateCommand(
	Long productId,
	Long sellerMemberId,
	String title,
	String description,
	String categoryCode,
	String imageUrl
) {

	/** 바꿀 값이 하나도 없으면 호출할 이유가 없습니다. */
	public boolean hasNoChange() {
		return title == null
			&& description == null
			&& categoryCode == null
			&& imageUrl == null;
	}
}
