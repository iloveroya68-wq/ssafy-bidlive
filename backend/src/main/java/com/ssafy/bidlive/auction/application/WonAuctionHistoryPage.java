package com.ssafy.bidlive.auction.application;

import java.util.List;

/** 판매자 닉네임 공개 규칙을 적용하기 전 낙찰 경매 이력 한 페이지다. */
public record WonAuctionHistoryPage(
	List<WonAuctionHistoryRow> rows,
	String nextCursor,
	boolean hasNext
) {

	public WonAuctionHistoryPage {
		rows = List.copyOf(rows);
	}
}
