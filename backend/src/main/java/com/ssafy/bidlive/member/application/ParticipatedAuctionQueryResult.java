package com.ssafy.bidlive.member.application;

import java.util.List;

/** 참여 경매 이력과 다음 커서 정보를 담은 유스케이스 결과다. */
public record ParticipatedAuctionQueryResult(
	List<ParticipatedAuctionItemResult> items,
	String nextCursor,
	boolean hasNext
) {

	public ParticipatedAuctionQueryResult {
		items = List.copyOf(items);
	}
}
