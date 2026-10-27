package com.ssafy.bidlive.member.api.dto;

import java.util.List;

import com.ssafy.bidlive.member.application.ParticipatedAuctionQueryResult;

/** 참여 경매 목록과 커서 페이지 정보를 반환한다. */
public record ParticipatedAuctionQueryResponse(
	List<ParticipatedAuctionItemResponse> items,
	String nextCursor,
	boolean hasNext
) {

	public ParticipatedAuctionQueryResponse {
		items = List.copyOf(items);
	}

	public static ParticipatedAuctionQueryResponse from(
		ParticipatedAuctionQueryResult result
	) {
		return new ParticipatedAuctionQueryResponse(
			result.items().stream()
				.map(ParticipatedAuctionItemResponse::from)
				.toList(),
			result.nextCursor(),
			result.hasNext()
		);
	}
}
