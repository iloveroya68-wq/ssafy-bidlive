package com.ssafy.bidlive.member.api.dto;

/** 참여 경매 조회 실패 응답이다. */
public record ParticipatedAuctionQueryErrorResponse(
	String code,
	String message
) {
}
