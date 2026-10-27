package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionCategoryResponse;
import com.ssafy.bidlive.auction.application.AuctionCategoryQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 경매방 생성과 목록 필터에 사용하는 공개 카테고리 목록을 반환한다. */
@RestController
@RequestMapping("/api/v1/auctions/categories")
public class AuctionCategoryController {

	private final AuctionCategoryQueryService categoryQueryService;

	public AuctionCategoryController(
		AuctionCategoryQueryService categoryQueryService
	) {
		this.categoryQueryService = categoryQueryService;
	}

	@GetMapping
	public AuctionCategoryResponse getCategories() {
		return AuctionCategoryResponse.from(categoryQueryService.getCategories());
	}
}
