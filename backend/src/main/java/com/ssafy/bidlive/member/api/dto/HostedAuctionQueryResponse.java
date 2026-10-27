package com.ssafy.bidlive.member.api.dto;

import java.util.List;

import com.ssafy.bidlive.member.application.HostedAuctionQueryResult;

/** 개최 경매 목록과 커서 페이지 정보를 반환한다. */
public record HostedAuctionQueryResponse(
	List<HostedAuctionItemResponse> items,
	String nextCursor,
	boolean hasNext
) {

	public HostedAuctionQueryResponse {
		items = List.copyOf(items);
	}

	public static HostedAuctionQueryResponse from(
		HostedAuctionQueryResult result
	) {
		return new HostedAuctionQueryResponse(
			result.items().stream()
				.map(HostedAuctionItemResponse::from)
				.toList(),
			result.nextCursor(),
			result.hasNext()
		);
	}
}
