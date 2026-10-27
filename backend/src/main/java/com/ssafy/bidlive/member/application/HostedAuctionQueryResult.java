package com.ssafy.bidlive.member.application;

import java.util.List;

/** 개최 경매 이력과 다음 커서 정보를 담은 유스케이스 결과다. */
public record HostedAuctionQueryResult(
	List<HostedAuctionItemResult> items,
	String nextCursor,
	boolean hasNext
) {

	public HostedAuctionQueryResult {
		items = List.copyOf(items);
	}
}
