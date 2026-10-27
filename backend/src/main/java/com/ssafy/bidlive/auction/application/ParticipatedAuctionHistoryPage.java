package com.ssafy.bidlive.auction.application;

import java.util.List;

/** 결과 공개 규칙을 적용하기 전 참여 경매 이력 한 페이지다. */
public record ParticipatedAuctionHistoryPage(
	List<ParticipatedAuctionHistoryRow> rows,
	String nextCursor,
	boolean hasNext
) {

	public ParticipatedAuctionHistoryPage {
		rows = List.copyOf(rows);
	}
}
