package com.ssafy.bidlive.auction.application;

import java.util.List;

/** 회원 도메인에 전달하는 개최 경매 이력의 커서 페이지다. */
public record HostedAuctionHistorySlice(
	List<HostedAuctionHistoryItem> items,
	String nextCursor,
	boolean hasNext
) {

	public HostedAuctionHistorySlice {
		items = List.copyOf(items);
	}
}
