package com.ssafy.bidlive.member.api.dto;

import java.util.List;

import com.ssafy.bidlive.member.application.WonAuctionQueryResult;

/** 낙찰 받은 경매 목록과 커서 페이지 정보를 반환한다. */
public record WonAuctionQueryResponse(
	List<WonAuctionItemResponse> items,
	String nextCursor,
	boolean hasNext
) {

	public WonAuctionQueryResponse {
		items = List.copyOf(items);
	}

	public static WonAuctionQueryResponse from(
		WonAuctionQueryResult result
	) {
		return new WonAuctionQueryResponse(
			result.items().stream()
				.map(WonAuctionItemResponse::from)
				.toList(),
			result.nextCursor(),
			result.hasNext()
		);
	}
}
