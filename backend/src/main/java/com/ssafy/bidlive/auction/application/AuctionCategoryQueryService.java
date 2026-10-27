package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionCategoryErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 활성 상품 카테고리를 프론트 표시 순서대로 제공한다. */
@Service
public class AuctionCategoryQueryService {

	private final AuctionCategoryQueryPersistence categoryQueryPersistence;

	public AuctionCategoryQueryService(
		AuctionCategoryQueryPersistence categoryQueryPersistence
	) {
		this.categoryQueryPersistence = categoryQueryPersistence;
	}

	@Transactional(readOnly = true)
	public AuctionCategoryQueryResult getCategories() {
		try {
			return new AuctionCategoryQueryResult(
				categoryQueryPersistence.findActiveCategories()
			);
		} catch (RuntimeException exception) {
			throw new AuctionCategoryQueryException(
				AuctionCategoryErrorCode.CATEGORY_SERVICE_UNAVAILABLE,
				exception
			);
		}
	}
}
