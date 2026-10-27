package com.ssafy.bidlive.auction.api.dto;

import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionCategoryQueryResult;

/** 활성 상품 카테고리 목록 응답이다. */
public record AuctionCategoryResponse(List<AuctionCategoryItemResponse> items) {

	public AuctionCategoryResponse {
		items = List.copyOf(items);
	}

	public static AuctionCategoryResponse from(
		AuctionCategoryQueryResult result
	) {
		return new AuctionCategoryResponse(result.items().stream()
			.map(AuctionCategoryItemResponse::from)
			.toList());
	}
}
