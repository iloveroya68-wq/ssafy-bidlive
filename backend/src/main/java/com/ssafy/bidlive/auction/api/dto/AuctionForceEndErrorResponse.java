package com.ssafy.bidlive.auction.api.dto;

/** 강제 종료 실패 응답. */
public record AuctionForceEndErrorResponse(
	String code,
	String message
) {
}
