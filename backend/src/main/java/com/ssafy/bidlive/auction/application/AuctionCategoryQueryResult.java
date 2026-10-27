package com.ssafy.bidlive.auction.application;

import java.util.List;

/** 카테고리 목록 조회 유스케이스의 결과다. */
public record AuctionCategoryQueryResult(List<AuctionCategoryItem> items) {

	public AuctionCategoryQueryResult {
		items = List.copyOf(items);
	}
}
