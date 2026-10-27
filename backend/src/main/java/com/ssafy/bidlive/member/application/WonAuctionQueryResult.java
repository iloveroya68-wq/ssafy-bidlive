package com.ssafy.bidlive.member.application;

import java.util.List;

/** 낙찰 경매 이력과 다음 커서 정보를 담은 유스케이스 결과다. */
public record WonAuctionQueryResult(
	List<WonAuctionItemResult> items,
	String nextCursor,
	boolean hasNext
) {

	public WonAuctionQueryResult {
		items = List.copyOf(items);
	}
}
