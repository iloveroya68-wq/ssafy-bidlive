package com.ssafy.bidlive.auction.application;

import java.util.List;

/** 회원 도메인에 전달하는 참여 경매 이력의 커서 페이지다. */
public record ParticipatedAuctionHistorySlice(
	List<ParticipatedAuctionHistoryItem> items,
	String nextCursor,
	boolean hasNext
) {

	public ParticipatedAuctionHistorySlice {
		items = List.copyOf(items);
	}
}
