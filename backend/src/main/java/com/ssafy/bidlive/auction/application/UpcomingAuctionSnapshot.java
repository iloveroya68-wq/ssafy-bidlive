package com.ssafy.bidlive.auction.application;

import java.util.List;

/** 시간 범위에 해당하는 전체 개수와 제한된 경매방 목록을 함께 전달합니다. */
public record UpcomingAuctionSnapshot(
	List<UpcomingAuctionRow> rows,
	long totalCount
) {

	public UpcomingAuctionSnapshot {
		rows = List.copyOf(rows);
	}
}
