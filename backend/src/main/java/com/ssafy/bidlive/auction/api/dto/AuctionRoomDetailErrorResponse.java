package com.ssafy.bidlive.auction.api.dto;

import java.time.Instant;

/** 경매방 상세 조회 실패 응답입니다. */
public record AuctionRoomDetailErrorResponse(
	Instant timestamp,
	int status,
	String code,
	String message
) {
}
