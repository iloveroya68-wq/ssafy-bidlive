package com.ssafy.bidlive.auction.api.dto;

import java.util.List;

import com.ssafy.bidlive.auction.application.UpcomingAuctionResult;

/** 곧 시작할 경매방과 전체·숨김 개수를 반환합니다. */
public record UpcomingAuctionResponse(
	List<UpcomingAuctionItemResponse> items,
	long totalCount,
	long hiddenCount
) {

	public UpcomingAuctionResponse {
		items = List.copyOf(items);
	}

	public static UpcomingAuctionResponse from(UpcomingAuctionResult result) {
		return new UpcomingAuctionResponse(
			result.items().stream()
				.map(UpcomingAuctionItemResponse::from)
				.toList(),
			result.totalCount(),
			result.hiddenCount()
		);
	}
}
