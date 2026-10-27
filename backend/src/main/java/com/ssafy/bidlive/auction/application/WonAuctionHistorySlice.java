package com.ssafy.bidlive.auction.application;

import java.util.List;

/** 회원 도메인에 전달하는 낙찰 경매 이력의 커서 페이지다. */
public record WonAuctionHistorySlice(
	List<WonAuctionHistoryItem> items,
	String nextCursor,
	boolean hasNext
) {

	public WonAuctionHistorySlice {
		items = List.copyOf(items);
	}
}
