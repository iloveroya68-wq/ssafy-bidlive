package com.ssafy.bidlive.auction.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionRoomListResult;

/** 경매방 목록 조회 응답이다. */
public record AuctionRoomListResponse(
	OffsetDateTime serverTime,
	List<AuctionRoomSummaryResponse> items,
	String nextCursor,
	boolean hasNext,
	AuctionRoomStatusCountsResponse counts
) {

	public AuctionRoomListResponse {
		items = List.copyOf(items);
	}

	public static AuctionRoomListResponse from(AuctionRoomListResult result) {
		return new AuctionRoomListResponse(
			AuctionRoomSummaryResponse.atKorea(result.serverTime()),
			result.items().stream()
				.map(AuctionRoomSummaryResponse::from)
				.toList(),
			result.nextCursor(),
			result.hasNext(),
			AuctionRoomStatusCountsResponse.from(result.counts())
		);
	}
}
