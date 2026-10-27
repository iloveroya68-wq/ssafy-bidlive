package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.UpcomingAuctionResponse;
import com.ssafy.bidlive.auction.application.UpcomingAuctionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증 없이 곧 시작할 공개 대기 경매방을 반환합니다. */
@RestController
@RequestMapping("/api/v1/auction-rooms")
public class UpcomingAuctionController {

	private final UpcomingAuctionQueryService queryService;

	public UpcomingAuctionController(UpcomingAuctionQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping("/upcoming")
	public UpcomingAuctionResponse getUpcoming(
		@RequestParam(required = false) Integer limit
	) {
		return UpcomingAuctionResponse.from(queryService.getUpcoming(limit));
	}
}
