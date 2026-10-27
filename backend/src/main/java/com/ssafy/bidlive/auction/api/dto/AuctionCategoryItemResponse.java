package com.ssafy.bidlive.auction.api.dto;

import com.ssafy.bidlive.auction.application.AuctionCategoryItem;

/** 경매방 카테고리 목록의 개별 항목이다. */
public record AuctionCategoryItemResponse(
	Long categoryId,
	String code,
	String name,
	int displayOrder
) {

	public static AuctionCategoryItemResponse from(AuctionCategoryItem item) {
		return new AuctionCategoryItemResponse(
			item.categoryId(),
			item.code(),
			item.name(),
			item.displayOrder()
		);
	}
}
