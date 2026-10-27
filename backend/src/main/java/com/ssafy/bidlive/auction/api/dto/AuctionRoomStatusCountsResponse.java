package com.ssafy.bidlive.auction.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.bidlive.auction.application.AuctionRoomStatusCounts;

/** 탭에 표시하는 상태별 방 개수다. 명세가 키를 상태 enum 이름으로 정했다. */
public record AuctionRoomStatusCountsResponse(
	@JsonProperty("LIVE") long live,
	@JsonProperty("WAITING") long waiting
) {

	public static AuctionRoomStatusCountsResponse from(AuctionRoomStatusCounts counts) {
		return new AuctionRoomStatusCountsResponse(counts.live(), counts.waiting());
	}
}
