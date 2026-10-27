package com.ssafy.bidlive.auction.api.dto;

/** 경매방 카테고리 목록 조회 오류 응답이다. */
public record AuctionCategoryErrorResponse(
	String code,
	String message
) {
}
