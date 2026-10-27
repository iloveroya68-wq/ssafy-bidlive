package com.ssafy.bidlive.auction.application;

/** 경매방 생성과 목록 필터에 사용하는 활성 상품 카테고리 한 건이다. */
public record AuctionCategoryItem(
	Long categoryId,
	String code,
	String name,
	int displayOrder
) {
}
