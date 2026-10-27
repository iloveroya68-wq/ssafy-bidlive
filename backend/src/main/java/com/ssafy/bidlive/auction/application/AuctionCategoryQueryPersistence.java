package com.ssafy.bidlive.auction.application;

import java.util.List;

/** 활성 상품 카테고리를 표시 순서대로 조회하는 영속성 계약이다. */
public interface AuctionCategoryQueryPersistence {

	List<AuctionCategoryItem> findActiveCategories();
}
