package com.ssafy.bidlive.auction.application;

import java.util.List;

/** 곧 시작할 경매방 목록과 숨겨진 방 개수를 반환합니다. */
public record UpcomingAuctionResult(
	List<UpcomingAuctionItem> items,
	long totalCount,
	long hiddenCount
) {

	public UpcomingAuctionResult {
		items = List.copyOf(items);
	}
}
