package com.ssafy.bidlive.auction.api.dto;

/** 경매방 목록 조회 오류 응답이다. */
public record AuctionRoomListErrorResponse(
	String code,
	String message
) {
}
