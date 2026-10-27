package com.ssafy.bidlive.auction.application;

import java.util.List;

/** 공개 규칙을 적용하기 전 개최 경매 이력 한 페이지다. */
public record HostedAuctionHistoryPage(
	List<HostedAuctionHistoryRow> rows,
	String nextCursor,
	boolean hasNext
) {

	public HostedAuctionHistoryPage {
		rows = List.copyOf(rows);
	}
}
